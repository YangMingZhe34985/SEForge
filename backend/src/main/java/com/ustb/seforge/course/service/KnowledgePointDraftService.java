package com.ustb.seforge.course.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.ai.application.*;
import com.ustb.seforge.common.exception.*;
import com.ustb.seforge.content.domain.DocumentStatus;
import com.ustb.seforge.content.repository.*;
import com.ustb.seforge.course.domain.KnowledgePoint;
import com.ustb.seforge.course.repository.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgePointDraftService {
    public record Source(Long id,Long documentId,Long resourceId,Long chapterId,String name,Integer page,String section,String quote,String vectorId){}
    public record Proposal(String name,String description,String importance,List<Long> sourceIds,boolean manual){}
    public record Coverage(int documents,int totalChunks,int selectedChunks,boolean sampled){}
    public record Draft(String id,Long courseId,Long chapterId,Long ownerId,Long traceId,List<Source> sources,List<Proposal> points,Coverage coverage){}
    @org.springframework.beans.factory.annotation.Value("${seforge.course.knowledge-point-timeout:300s}")
    private Duration generationTimeout=Duration.ofSeconds(300);
    @jakarta.annotation.PostConstruct void validateTimeout(){
        if(generationTimeout.compareTo(Duration.ofSeconds(60))<0||generationTimeout.compareTo(Duration.ofSeconds(600))>0)
            throw new IllegalArgumentException("Knowledge point timeout must be 60s to 600s");
    }
    private final CourseAccessService access;
    private final CourseChapterRepository chapters;
    private final CourseRepository courses;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;
    private final KnowledgePointRepository points;
    private final AiGateway ai;
    private final PromptCatalog prompts;
    private final ObjectMapper json;
    private final org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisProvider;
    private final JdbcTemplate jdbc;
    public KnowledgePointDraftService(CourseAccessService access,CourseChapterRepository chapters,CourseRepository courses,
            KnowledgeDocumentRepository documents,KnowledgeChunkRepository chunks,KnowledgePointRepository points,
            AiGateway ai,PromptCatalog prompts,ObjectMapper json,org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redis,JdbcTemplate jdbc){
        this.access=access;this.chapters=chapters;this.courses=courses;this.documents=documents;this.chunks=chunks;
        this.points=points;this.ai=ai;this.prompts=prompts;this.json=json;this.redisProvider=redis;this.jdbc=jdbc;
    }
    private void authorize(Long course,Long chapter,Long owner){
        access.requireTeachingStaff(course,owner);
        chapters.findById(chapter).filter(c->c.getCourseId().equals(course)).orElseThrow(this::missing);
    }
    public Draft generate(Long course,Long chapter,Long owner){
        authorize(course,chapter,owner);
        List<List<Source>> documentSources=new ArrayList<>();
        for(var doc:documents.findAllByCourseIdOrderByCreatedAtDesc(course)){
            if(doc.getStatus()!=DocumentStatus.READY || !chapter.equals(doc.getChapterId()))continue;
            List<Source> sourceList=new ArrayList<>();
            for(var chunk:chunks.findAllByDocumentIdAndEmbeddingVersionOrderByChunkIndexAsc(doc.getId(),doc.getEmbeddingVersion())){
                String quote=chunk.getContent();if(quote==null||quote.isBlank())continue;
                sourceList.add(new Source(chunk.getId(),doc.getId(),doc.getResourceId(),chapter,
                        doc.getOriginalName(),chunk.getPage(),chunk.getSection(),quote,chunk.getVectorId()));
            }
            documentSources.add(sourceList);
        }
        var selection=ChapterEvidenceSelector.select(documentSources);
        List<Source> sources=selection.sources();
        if(sources.isEmpty())throw new AppException(ErrorCode.CONFLICT,"章节没有 READY 的知识文档，请先上传并等待摄取完成");
        // Refuse before incurring model cost if temporary draft storage is down.
        try{redis().hasKey("seforge:kp-draft:health");}catch(RuntimeException e){throw unavailable();}
        var template=prompts.load("knowledge-point","v2");
        var response=generateResponse(new AiRequest(ModelCapability.REASONING,owner,course,template.identifier(),template.text(),encode(sources)),generationTimeout);
        var parsed=parseDraft(course,chapter,owner,sources,response);
        var draft=new Draft(parsed.id(),course,chapter,owner,parsed.traceId(),sources,parsed.points(),
                new Coverage(selection.documents(),selection.totalChunks(),sources.size(),selection.sampled()));
        authorize(course,chapter,owner);
        try{redis().opsForValue().set(key(draft.id()),encode(draft),Duration.ofHours(24));}catch(RuntimeException e){throw unavailable();}
        return draft;
    }
    // Reuse the proven streaming transport, but expose only a fully validated draft to callers.
    // The local deadline includes all Runtime retry/fallback attempts, before the browser's 630s ceiling.
    AiResponse generateResponse(AiRequest request,Duration timeout){
        CompletableFuture<AiResponse> result=new CompletableFuture<>();
        AiStreamHandle handle;
        Duration attemptTimeout=timeout.compareTo(Duration.ofSeconds(180))>0?Duration.ofSeconds(180):timeout;
        try{handle=ai.stream(request,attemptTimeout,ignored->{},result::complete,result::completeExceptionally);}
        catch(RuntimeException e){throw providerFailure(e);}
        try{return result.get(timeout.toMillis(),TimeUnit.MILLISECONDS);}
        catch(TimeoutException e){handle.cancel();throw new AppException(ErrorCode.KNOWLEDGE_POINT_TIMEOUT,"知识点模型生成超时，已停止本次调用；请检查供应商连通性或稍后重试");}
        catch(InterruptedException e){handle.cancel();Thread.currentThread().interrupt();throw new AppException(ErrorCode.KNOWLEDGE_POINT_TIMEOUT,"知识点生成已中断，请稍后重试");}
        catch(ExecutionException e){throw providerFailure(e.getCause());}
    }
    private AppException providerFailure(Throwable failure){
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        String reason="UNAVAILABLE";
        for(Throwable cause=failure;cause!=null&&seen.add(cause);cause=cause.getCause()){
            String name=cause.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if(name.contains("timeout"))reason="TIMEOUT";
            else if(name.contains("authentication")||name.contains("authorization"))reason="AUTHENTICATION";
            else if(name.contains("ratelimit"))reason="RATE_LIMIT";
        }
        return new AppException(reason.equals("TIMEOUT")?ErrorCode.KNOWLEDGE_POINT_TIMEOUT:ErrorCode.KNOWLEDGE_POINT_PROVIDER_FAILED,
                "知识点模型调用失败（"+reason+"），请检查 REASONING 供应商配置、配额及连通性",Map.of("stage","PROVIDER","reason",reason));
    }
    Draft parseDraft(Long course,Long chapter,Long owner,List<Source> sources,AiResponse response){
        List<Proposal> proposals=new ArrayList<>();
        try{
            String text=response.text()==null?"":response.text().trim();
            if(text.startsWith("```json\n")&&text.endsWith("```"))text=text.substring(8,text.length()-3).trim();
            else if(text.startsWith("```\n")&&text.endsWith("```"))text=text.substring(4,text.length()-3).trim();
            var root=json.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);
            if(root==null||!root.isObject())throw invalid("Expected a JSON object");
            var array=root.path("points");
            if(!array.isArray()||array.isEmpty()||array.size()>30)throw invalid("Invalid knowledge-point draft structure");
            for(var node:array){
                if(!node.isObject()||!node.path("name").isTextual()||!node.path("description").isTextual()||!node.path("importance").isTextual())throw invalid("Invalid field types");
                List<Long> ids=new ArrayList<>();var refs=node.path("sourceIds");
                if(!refs.isArray())throw invalid("Draft must cite chapter sources");
                for(var ref:refs){
                    // Public Long IDs are serialized as strings; accept either exact representation.
                    if(ref.isIntegralNumber()&&ref.canConvertToLong())ids.add(ref.longValue());
                    else if(ref.isTextual()&&ref.textValue().matches("[0-9]{1,19}")){
                        try{ids.add(Long.parseLong(ref.textValue()));}catch(NumberFormatException e){throw invalid("Invalid source ID");}
                    }else throw invalid("Invalid source ID");
                }
                proposals.add(new Proposal(node.path("name").asText(),node.path("description").asText(),node.path("importance").asText(),ids,false));
            }
        }catch(com.fasterxml.jackson.core.JsonProcessingException|AppException e){throw outputFailure(response.traceId());}
        var draft=new Draft(UUID.randomUUID().toString(),course,chapter,owner,response.traceId(),List.copyOf(sources),List.copyOf(proposals),null);
        try{validate(draft,proposals);}catch(AppException e){throw outputFailure(response.traceId());}
        return draft;
    }
    private AppException outputFailure(Long trace){return new AppException(ErrorCode.KNOWLEDGE_POINT_INVALID_OUTPUT,
            "模型返回的知识点草稿结构或来源引用无效，未保存草稿，请重新生成",trace==null?Map.of("stage","STRUCTURE"):Map.of("stage","STRUCTURE","aiTraceId",trace));}
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<Long> confirm(Long course,Long chapter,Long owner,String draftId,List<Proposal> selection){
        authorize(course,chapter,owner);
        courses.findForUpdate(course).orElseThrow(this::missing);
        var existing=jdbc.queryForList("SELECT result_ids FROM knowledge_point_confirmation WHERE draft_key=? AND course_id=? AND chapter_id=? AND owner_id=?",String.class,draftId,course,chapter,owner);
        if(!existing.isEmpty())return ids(existing.getFirst());
        String stored;String draftKey=key(draftId);
        try{stored=redis().opsForValue().get(draftKey);}catch(RuntimeException e){throw unavailable();}
        if(stored==null)throw new AppException(ErrorCode.CONFLICT,"草稿已过期，请重新生成");
        Draft draft;
        try{draft=json.readValue(stored,Draft.class);}catch(Exception e){throw invalid("Invalid stored draft");}
        if(!course.equals(draft.courseId())||!chapter.equals(draft.chapterId())||!owner.equals(draft.ownerId()))throw missing();
        validate(draft,selection);
        Map<Long,Source> available=new HashMap<>();draft.sources().forEach(s->available.put(s.id(),s));
        List<Long> result=new ArrayList<>();int order=points.findAllByCourseIdOrderBySortOrderAscIdAsc(course).size();
        for(var item:selection){
            List<Source> cited=item.sourceIds().stream().map(available::get).toList();
            for(var source:cited){
                var doc=documents.findByIdAndCourseId(source.documentId(),course).orElseThrow(this::missing);
                if(doc.getStatus()!=DocumentStatus.READY||!chapter.equals(doc.getChapterId())||chunks.findByVectorIdAndCourseIdAndEmbeddingVersion(source.vectorId(),course,doc.getEmbeddingVersion()).isEmpty())
                    throw new AppException(ErrorCode.CONFLICT,"来源文档或索引已变化，请重新生成草稿");
            }
            var point=new KnowledgePoint(course,chapter,item.name().trim(),item.description(),++order);
            point.provenance(item.importance(),encode(cited));result.add(points.save(point).getId());
        }
        jdbc.update("INSERT INTO knowledge_point_confirmation(version,created_at,updated_at,course_id,chapter_id,owner_id,draft_key,trace_id,result_ids) VALUES(0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,?,?,?,?,?)",course,chapter,owner,draftId,draft.traceId(),encode(result));
        return result;
    }
    private void validate(Draft draft,List<Proposal> selection){
        if(selection==null||selection.isEmpty()||selection.size()>50)throw invalid("请选择 1 至 50 个知识点");
        Set<Long> available=new HashSet<>();draft.sources().forEach(s->available.add(s.id()));Set<String> names=new HashSet<>();
        for(var p:selection){
            if(p==null||p.name()==null||p.name().isBlank()||p.name().length()>160||!names.add(p.name().trim())||p.description()==null||p.description().length()>5000
                    ||p.importance()==null||!Set.of("CORE","IMPORTANT","OPTIONAL").contains(p.importance())||p.sourceIds()==null||p.sourceIds().size()>40
                    ||(!p.manual()&&p.sourceIds().isEmpty())||!available.containsAll(p.sourceIds())||new HashSet<>(p.sourceIds()).size()!=p.sourceIds().size())
                throw invalid("知识点名称、级别或来源引用无效；AI 草稿必须保留章节内来源");
        }
    }
    private StringRedisTemplate redis(){var value=redisProvider.getIfAvailable();if(value==null)throw unavailable();return value;}
    private String key(String id){if(id==null||!id.matches("[a-f0-9-]{36}"))throw missing();return "seforge:kp-draft:"+id;}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw invalid("Cannot encode draft");}}
    private List<Long> ids(String value){try{return json.readValue(value,new com.fasterxml.jackson.core.type.TypeReference<List<Long>>(){});}catch(Exception e){throw invalid("Invalid confirmation result");}}
    private AppException missing(){return new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Chapter draft not found");}
    private AppException invalid(String text){return new AppException(ErrorCode.VALIDATION_FAILED,text);}
    private AppException unavailable(){return new AppException(ErrorCode.KNOWLEDGE_POINT_REDIS_UNAVAILABLE,"知识点草稿暂存服务不可用，请检查 Redis 后重试",Map.of("stage","REDIS"));}
}
