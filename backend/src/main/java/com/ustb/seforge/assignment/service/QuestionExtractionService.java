package com.ustb.seforge.assignment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.ai.application.*;
import com.ustb.seforge.assignment.api.*;
import com.ustb.seforge.assignment.domain.*;
import com.ustb.seforge.assignment.repository.*;
import com.ustb.seforge.common.audit.AuditService;
import com.ustb.seforge.common.exception.*;
import com.ustb.seforge.content.service.*;
import com.ustb.seforge.course.service.CourseAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/** Extraction is advisory. Only confirm() writes existing AssignmentQuestion entities. */
@Service
public class QuestionExtractionService {
    @org.springframework.beans.factory.annotation.Value("${seforge.assignment.extraction-timeout:300s}")
    private java.time.Duration extractionTimeout = java.time.Duration.ofSeconds(300);
    @jakarta.annotation.PostConstruct void validateTimeout() {
        if (extractionTimeout.compareTo(java.time.Duration.ofSeconds(60)) < 0 || extractionTimeout.compareTo(java.time.Duration.ofSeconds(480)) > 0)
            throw new IllegalArgumentException("Question extraction timeout must be 60s to 480s");
    }
    private final AssignmentService assignments;
    private final AssignmentRepository assignmentRepository;
    private final QuestionImportDraftRepository drafts;
    private final AssignmentMediaService media;
    private final CourseAccessService access;
    private final DocumentParserService parser;
    private final MultimodalContentProcessor vision;
    private final AiGateway ai;
    private final PromptCatalog prompts;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final TransactionTemplate tx;
    public QuestionExtractionService(AssignmentService assignments,AssignmentRepository assignmentRepository,
            QuestionImportDraftRepository drafts,AssignmentMediaService media,CourseAccessService access,
            DocumentParserService parser,MultimodalContentProcessor vision,AiGateway ai,PromptCatalog prompts,ObjectMapper mapper,
            AuditService audit,PlatformTransactionManager transactions) {
        this.assignments=assignments;this.assignmentRepository=assignmentRepository;this.drafts=drafts;
        this.media=media;this.access=access;this.parser=parser;this.vision=vision;this.ai=ai;this.prompts=prompts;this.mapper=mapper;
        this.audit=audit;this.tx=new TransactionTemplate(transactions);
        this.tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public record Extracted(@NotEmpty @Size(max=50) List<@Valid ExtractedQuestion> questions) {}
    // Empty strings/arrays explicitly mean unknown. Strict AI Runtime rejects missing/null record fields.
    public record ExtractedQuestion(@NotBlank String type,@NotNull @Size(max=10000) String contentMarkdown,
            @NotNull @Size(max=30) List<@NotBlank @Size(max=10000) String> choices,
            @NotNull List<Integer> correctChoiceIndexes,@NotNull String booleanAnswer,
            @NotNull @Size(max=10000) String referenceAnswer,@NotNull String answerEvidence,
            @NotNull String score,@NotNull String scoreEvidence,@Min(1) int sourcePage,
            @NotNull @Size(max=4) List<Double> boundingBox,@NotNull @Size(max=2000) String warning) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record DraftQuestion(String draftKey,QuestionType type,String contentMarkdown,List<Choice> choices,
            Object correctAnswer,String referenceAnswer,BigDecimal score,int order,Long sourceFile,
            int sourcePage,List<Double> sourceRegion,List<String> warnings) {}
    public record Choice(String id,String label) {}
    public record ImportSource(Long id,String name) {}
    public record SourceText(Long sourceFile,List<DocumentParserService.ParsedSection> sections) {}
    public record ImportView(Long id,Long sourceFile,String sourceName,List<DraftQuestion> questions,boolean confirmed,List<ImportSource> sources) {}
    public record ConfirmItem(@NotBlank String draftKey,@NotNull @Valid UpsertQuestionRequest question) {}
    public record ConfirmRequest(@NotEmpty @Size(max=50) List<@Valid ConfirmItem> questions) {}
    public record ReferenceDraft(Long sourceFile,String markdown,Long traceId,String warning) {}

    public ImportView extract(Long assignmentId,Long userId,Long sourceId,boolean reparse) {
        return extract(assignmentId,userId,List.of(sourceId),reparse,null);
    }
    public ImportView extract(Long assignmentId,Long userId,List<Long> sourceIds,boolean reparse,Long reparseFile) {
        var assignment=authorized(assignmentId,userId);
        if(sourceIds==null||sourceIds.isEmpty()||sourceIds.size()>3||new HashSet<>(sourceIds).size()!=sourceIds.size())
            throw QuestionContent.invalid("每批限 1～3 张图片，或 1 份 PDF/Markdown；来源不得重复");
        var assets=sourceIds.stream().map(id->media.bound(id,assignmentId,AssignmentMedia.Purpose.IMPORT,null,null)).toList();
        if(assets.stream().anyMatch(a->!a.getOwnerId().equals(userId)))throw missing();
        if(assets.size()>1&&assets.stream().anyMatch(a->!a.getMediaType().startsWith("image/")))
            throw QuestionContent.invalid("多文件导入仅支持 PNG/JPEG 图片，PDF/Markdown 请单独导入");
        Long sourceId=sourceIds.getFirst();var asset=assets.getFirst();
        var existing=drafts.findBySourceMediaId(sourceId);
        List<SourceText> cached=existing.map(d->d.getSourceSectionsJson()==null?List.<SourceText>of():readList(d.getSourceSectionsJson(),SourceText.class)).orElse(List.of());
        if(existing.isPresent()) {
            owned(existing.get(),assignmentId,userId);
            var previous=cached.isEmpty()?List.of(sourceId):cached.stream().map(SourceText::sourceFile).toList();
            if(!previous.equals(sourceIds)&&!reparse)throw new AppException(ErrorCode.CONFLICT,"该源文件已属于另一导入批次，请重新上传新批次");
            if(!reparse&&reparseFile==null)return view(existing.get(),asset);
            if(existing.get().getResultJson()!=null)throw new AppException(ErrorCode.CONFLICT,"已确认的导入不能重新解析，请编辑正式题目");
        }
        if(reparseFile!=null&&(existing.isEmpty()||!sourceIds.contains(reparseFile)||assets.stream().anyMatch(a->!a.getMediaType().startsWith("image/"))))
            throw QuestionContent.invalid("单图重解析只能选择当前未确认批次的图片");
        long expectedVersion=existing.map(QuestionImportDraft::getVersion).orElse(-1L);
        var sources=new ArrayList<SourceText>();
        for(var a:assets) {
            var saved=cached.stream().filter(c->c.sourceFile().equals(a.getId())).findFirst();
            sources.add(reparseFile!=null&&!a.getId().equals(reparseFile)&&saved.isPresent()?saved.get():new SourceText(a.getId(),readSections(assignment,userId,a)));
        }
        // Global PAGE numbers are server-owned. They map back to a file/local page; model IDs are never trusted.
        var sections=new ArrayList<DocumentParserService.ParsedSection>();
        var pageSources=new LinkedHashMap<Integer,Long>();var localPages=new HashMap<Integer,Integer>();
        for(var source:sources)for(var section:source.sections()) {
            int page=sections.size()+1;pageSources.put(page,source.sourceFile());localPages.put(page,section.page());
            sections.add(new DocumentParserService.ParsedSection(section.text(),page,section.section()));
        }
        String context=sections.stream().map(section->"[PAGE "+section.page()+"]\n"+section.text()).collect(java.util.stream.Collectors.joining("\n\n"));
        if(context.isBlank()||context.length()>60_000)throw QuestionContent.invalid("导入文本为空或超过 60000 字符，请拆分文件");
        Extracted result;var prompt=prompts.load("question-extraction","v2");
        try { result=ai.completeJsonStreaming(new AiRequest(ModelCapability.REASONING,userId,assignment.getCourseId(),
                prompt.identifier(),prompt.text(),context),extractionTimeout,Extracted.class,value->validate(value,sections),node->QuestionDraftNormalizer.normalize(node,sections)); }
        catch(AiUnavailableException e){throw diagnostic(e,"QUESTION_EXTRACTION");}
        var converted=new ArrayList<DraftQuestion>();
        for(var item:result.questions()) {
            Long source=pageSources.get(item.sourcePage());
            if(reparseFile==null||reparseFile.equals(source)) {
                var q=convert(item,source,converted.size());
                converted.add(new DraftQuestion(q.draftKey(),q.type(),q.contentMarkdown(),q.choices(),q.correctAnswer(),q.referenceAnswer(),q.score(),q.order(),source,localPages.get(item.sourcePage()),q.sourceRegion(),q.warnings()));
            }
        }
        if(reparseFile!=null) {
            if(converted.isEmpty())throw QuestionContent.invalid("选定图片未识别出题目，保留原草稿；请核对图片");
            readList(existing.orElseThrow().getDraftJson(),DraftQuestion.class).stream().filter(q->!q.sourceFile().equals(reparseFile)).forEach(converted::add);
            converted.sort(Comparator.comparingInt(q->sourceIds.indexOf(q.sourceFile())));
        }
        var ordered=new ArrayList<DraftQuestion>();
        for(var q:converted)ordered.add(new DraftQuestion(q.draftKey(),q.type(),q.contentMarkdown(),q.choices(),q.correctAnswer(),q.referenceAnswer(),q.score(),ordered.size(),q.sourceFile(),q.sourcePage(),q.sourceRegion(),q.warnings()));
        if(ordered.size()>50)throw QuestionContent.invalid("每批最多 50 道题目");
        return tx.execute(status->{
            var locked=assignmentRepository.findByIdForUpdate(assignmentId).orElseThrow(QuestionExtractionService::missing);
            access.requireTeachingStaff(locked.getCourseId(),userId);locked.requireDraft();
            var found=drafts.findBySourceMediaId(sourceId);
            if(found.isPresent()) {
                owned(found.get(),assignmentId,userId);
                if(found.get().getResultJson()!=null||found.get().getVersion()!=expectedVersion)
                    throw new AppException(ErrorCode.CONFLICT,"导入批次已更新或确认，请刷新后重试");
            }
            var stored=found.orElseGet(()->drafts.save(new QuestionImportDraft(assignmentId,userId,sourceId,json(ordered))));
            stored.replaceDraft(json(ordered));stored.sourceSections(json(sources));
            audit.record(userId,locked.getCourseId(),"QUESTION_DRAFT_EXTRACTED","QUESTION_IMPORT",stored.getId(),AuditService.SUCCEEDED);
            return view(stored,asset);
        });
    }
    public ImportView get(Long assignmentId,Long userId,Long id) {
        var assignment=assignments.require(assignmentId);access.requireTeachingStaff(assignment.getCourseId(),userId);
        var draft=owned(drafts.findById(id).orElseThrow(QuestionExtractionService::missing),assignmentId,userId);
        return view(draft,media.bound(draft.getSourceMediaId(),assignmentId,AssignmentMedia.Purpose.IMPORT,null,null));
    }
    public List<AssignmentQuestionView> confirm(Long assignmentId,Long userId,Long id,ConfirmRequest request) {
        return tx.execute(status->{
            var assignment=assignmentRepository.findByIdForUpdate(assignmentId).orElseThrow(QuestionExtractionService::missing);
            access.requireTeachingStaff(assignment.getCourseId(),userId);
            var draft=owned(drafts.findById(id).orElseThrow(QuestionExtractionService::missing),assignmentId,userId);
            String serialized=json(request);
            if(draft.getConfirmationJson()!=null) {
                if(!serialized.equals(draft.getConfirmationJson()))throw new AppException(ErrorCode.CONFLICT,"该导入已确认，不能重复导入不同内容");
                return readList(draft.getResultJson(),AssignmentQuestionView.class);
            }
            assignment.requireDraft();
            var allowed=readList(draft.getDraftJson(),DraftQuestion.class).stream().map(DraftQuestion::draftKey).collect(java.util.stream.Collectors.toSet());
            var seen=new HashSet<String>();var result=new ArrayList<AssignmentQuestionView>();
            for(var item:request.questions()) {
                if(!allowed.contains(item.draftKey())||!seen.add(item.draftKey()))throw QuestionContent.invalid("无效或重复的导入题目");
                result.add(assignments.addQuestion(assignmentId,userId,item.question()));
            }
            draft.confirm(serialized,json(result));
            audit.record(userId,assignment.getCourseId(),"QUESTIONS_IMPORT_CONFIRMED","QUESTION_IMPORT",id,AuditService.SUCCEEDED);
            return List.copyOf(result);
        });
    }
    public ReferenceDraft reference(Long assignmentId,Long userId,Long sourceId) {
        var assignment=authorized(assignmentId,userId);
        var asset=media.bound(sourceId,assignmentId,AssignmentMedia.Purpose.REFERENCE,null,null);
        if(!asset.getMediaType().startsWith("image/"))throw QuestionContent.invalid("请选择 PNG/JPEG 参考答案图片");
        MultimodalContentProcessor.NormalizedContent normalized;
        try { normalized=vision.normalize(userId,assignment.getCourseId(),sourceId,media.read(asset),asset.getMediaType()); }
        catch(AiUnavailableException e){throw diagnostic(e,"VISION");}
        return new ReferenceDraft(sourceId,normalized.text(),normalized.traceId(),normalized.limitations());
    }
    private Assignment authorized(Long id,Long user) {
        var value=assignments.require(id);access.requireTeachingStaff(value.getCourseId(),user);value.requireDraft();return value;
    }
    private List<DocumentParserService.ParsedSection> readSections(Assignment assignment,Long user,AssignmentMedia asset) {
        byte[] bytes=media.read(asset);
        try {
            if(asset.getMediaType().startsWith("image/"))return List.of(new DocumentParserService.ParsedSection(
                    vision.normalize(user,assignment.getCourseId(),asset.getId(),bytes,asset.getMediaType()).text(),1,"Image"));
            if(!asset.getFileName().toLowerCase(Locale.ROOT).endsWith(".pdf") && !asset.getFileName().toLowerCase(Locale.ROOT).endsWith(".md"))
                throw QuestionContent.invalid("智能导入仅支持 PDF、PNG、JPEG、Markdown");
            if(!asset.getMediaType().equals("application/pdf"))return parser.parse(asset.getFileName(),new ByteArrayInputStream(bytes)).stream()
                    .map(s->new DocumentParserService.ParsedSection(s.text(),1,s.section())).toList();
            try(var pdf=PDDocument.load(bytes)) {
                if(pdf.getNumberOfPages()>20)throw QuestionContent.invalid("单次导入最多 20 页，请拆分 PDF");
                var parsed=parser.parse(asset.getFileName(),new ByteArrayInputStream(bytes));
                var result=new ArrayList<DocumentParserService.ParsedSection>();var renderer=new PDFRenderer(pdf);int visionPages=0;
                for(int page=1;page<=pdf.getNumberOfPages();page++) {
                    final int number=page;
                    var text=parsed.stream().filter(s->s.page()==number).map(DocumentParserService.ParsedSection::text).findFirst().orElse("");
                    if(text.strip().length()<30) {
                        if(++visionPages>5)throw QuestionContent.invalid("扫描页超过 5 页，请拆分后导入以控制 Vision 成本");
                        var box=pdf.getPage(page-1).getCropBox();
                        float scale=Math.min(1.5f,2000f/Math.max(box.getWidth(),box.getHeight()));
                        var output=new ByteArrayOutputStream();
                        javax.imageio.ImageIO.write(renderer.renderImage(page-1,scale),"png",output);
                        text=vision.normalize(user,assignment.getCourseId(),asset.getId(),output.toByteArray(),"image/png").text();
                    }
                    result.add(new DocumentParserService.ParsedSection(text,page,"Page "+page));
                }
                return result;
            }
        }catch(AiUnavailableException e){throw diagnostic(e,"VISION");}
        catch(IOException e){throw new AppException(ErrorCode.VALIDATION_FAILED,"文件解析失败：请检查 PDF 是否损坏或加密");}
    }
    static void validate(Extracted value,List<DocumentParserService.ParsedSection> sections) {
        if(value==null||value.questions()==null||value.questions().isEmpty()||value.questions().size()>50)throw new IllegalArgumentException("Expected 1–50 questions");
        for(var q:value.questions()) {
            if(!q.type().equals("UNKNOWN"))QuestionType.valueOf(q.type());
            if(sections.stream().noneMatch(s->s.page()==q.sourcePage()))throw new IllegalArgumentException("Invalid source page");
            var text=sections.stream().map(DocumentParserService.ParsedSection::text).collect(java.util.stream.Collectors.joining("\n"));
            if(!q.score().isBlank()) {
                var score=new BigDecimal(q.score());if(score.signum()<=0||score.compareTo(new BigDecimal("100000"))>0||score.scale()>2||!QuestionDraftNormalizer.evidenceExists(q.scoreEvidence(),sections))throw new IllegalArgumentException("Score needs literal source evidence");
            }
            if((!q.referenceAnswer().isBlank()||!q.correctChoiceIndexes().isEmpty()||!q.booleanAnswer().isBlank())&&!QuestionDraftNormalizer.evidenceExists(q.answerEvidence(),sections))throw new IllegalArgumentException("Answer needs literal source evidence");
            if(!Set.of("","true","false").contains(q.booleanAnswer()))throw new IllegalArgumentException("Invalid boolean");
            if(new HashSet<>(q.correctChoiceIndexes()).size()!=q.correctChoiceIndexes().size()||q.correctChoiceIndexes().stream().anyMatch(i->i==null||i<0||i>=q.choices().size()))throw new IllegalArgumentException("Invalid choice index");
            if(q.type().equals("SINGLE_CHOICE")&&q.correctChoiceIndexes().size()>1)throw new IllegalArgumentException("Single choice answer cardinality");
            if(!q.boundingBox().isEmpty()&&(q.boundingBox().size()!=4||q.boundingBox().stream().anyMatch(v->v==null||!Double.isFinite(v)||v<0||v>1)||q.boundingBox().get(2)<=q.boundingBox().get(0)||q.boundingBox().get(3)<=q.boundingBox().get(1)))throw new IllegalArgumentException("Invalid normalized bounding box");
        }
    }
    private DraftQuestion convert(ExtractedQuestion q,Long source,int order) {
        var choices=q.choices().stream().map(label->new Choice("opt_"+UUID.randomUUID().toString().replace("-",""),label)).toList();
        Object correct=switch(q.type()){
            case "SINGLE_CHOICE"->q.correctChoiceIndexes().isEmpty()?null:choices.get(q.correctChoiceIndexes().getFirst()).id();
            case "MULTIPLE_CHOICE"->q.correctChoiceIndexes().isEmpty()?null:q.correctChoiceIndexes().stream().map(i->choices.get(i).id()).toList();
            case "TRUE_FALSE"->q.booleanAnswer().isBlank()?null:Boolean.valueOf(q.booleanAnswer());default->null;};
        var warnings=new ArrayList<String>();warnings.add("AI 草稿非权威：请对照原文件确认题型、题干、图形、答案与分值；原文件仅教师可见，不会自动附到学生题目。");
        if(!q.warning().isBlank())warnings.add(q.warning());
        if(q.type().equals("UNKNOWN"))warnings.add("题型未知，必须由教师选择");
        if(q.score().isBlank())warnings.add("原文未确认分值，请填写");
        if(correct==null&&q.referenceAnswer().isBlank())warnings.add("未识别到明确答案，留空待教师确认");
        return new DraftQuestion(UUID.randomUUID().toString(),q.type().equals("UNKNOWN")?null:QuestionType.valueOf(q.type()),q.contentMarkdown(),choices,correct,q.referenceAnswer(),q.score().isBlank()?null:new BigDecimal(q.score()),order,source,q.sourcePage(),q.boundingBox(),warnings);
    }
    private ImportView view(QuestionImportDraft draft,AssignmentMedia asset){
        var ids=draft.getSourceSectionsJson()==null?List.of(asset.getId()):readList(draft.getSourceSectionsJson(),SourceText.class).stream().map(SourceText::sourceFile).toList();
        var sources=ids.stream().map(id->media.bound(id,draft.getAssignmentId(),AssignmentMedia.Purpose.IMPORT,null,null)).map(a->new ImportSource(a.getId(),a.getFileName())).toList();
        return new ImportView(draft.getId(),asset.getId(),asset.getFileName(),readList(draft.getDraftJson(),DraftQuestion.class),draft.getResultJson()!=null,sources);
    }
    private QuestionImportDraft owned(QuestionImportDraft draft,Long assignment,Long user){if(!draft.getAssignmentId().equals(assignment)||!draft.getOwnerId().equals(user))throw missing();return draft;}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(IOException e){throw new IllegalStateException(e);}}
    private <T> List<T> readList(String value,Class<T> type){try{return mapper.readValue(value,mapper.getTypeFactory().constructCollectionType(List.class,type));}catch(IOException e){throw new IllegalStateException(e);}}
    private static AppException missing(){return new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Question import not found");}
    static AppException diagnostic(AiUnavailableException failure,String stage) {
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable cause=failure;cause!=null&&seen.add(cause);cause=cause.getCause()) {
            if(cause instanceof QuestionDraftNormalizer.Invalid invalid) {
                org.slf4j.LoggerFactory.getLogger(QuestionExtractionService.class).warn("Question import validation stage={} question={} field={} reason={}",stage,invalid.question,invalid.field,invalid.reason);
                return new AppException(ErrorCode.QUESTION_EXTRACTION_INVALID_OUTPUT,
                        "导题结果无法解析："+(invalid.question>0?"第 "+invalid.question+" 题，":"")+"字段 "+invalid.field+"（"+invalid.reason+"），请重试或调整原文件",
                        Map.of("stage",stage,"question",invalid.question,"field",invalid.field,"reason",invalid.reason));
            }
            if(cause instanceof com.fasterxml.jackson.core.JsonProcessingException) {
                return new AppException(ErrorCode.QUESTION_EXTRACTION_INVALID_OUTPUT,"模型返回的 JSON 不完整或字段类型不匹配，请减少单次题量后重试",Map.of("stage",stage,"reason","INVALID_JSON"));
            }
            String name=cause.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if(name.contains("timeout"))return new AppException(ErrorCode.QUESTION_EXTRACTION_TIMEOUT,
                    stage.equals("VISION")?"图片理解超时，请稍后重试或缩小图片":"图片/文档已读取，但题目结构化生成超时；本次生成已停止，请减少单次题量后重试",
                    Map.of("stage",stage,"reason","TIMEOUT"));
            if(cause instanceof IllegalArgumentException || cause instanceof com.fasterxml.jackson.core.JsonProcessingException)
                return new AppException(ErrorCode.QUESTION_EXTRACTION_INVALID_OUTPUT,"模型返回的草稿结构或来源证据无效，请重试或手动录入",Map.of("stage",stage,"reason","INVALID_OUTPUT"));
            if(name.contains("authentication")||name.contains("authorization"))return new AppException(ErrorCode.AI_UNAVAILABLE,
                    "模型服务鉴权失败，请检查对应供应商密钥与模型访问权限",Map.of("stage",stage,"reason","AUTHENTICATION"));
            if(name.contains("ratelimit"))return new AppException(ErrorCode.AI_UNAVAILABLE,
                    "模型服务限流或额度不足，请检查配额并稍后重试",Map.of("stage",stage,"reason","RATE_LIMIT"));
        }
        return new AppException(ErrorCode.AI_UNAVAILABLE,stage.equals("VISION")?"图片理解服务不可用，请检查 DashScope Vision 模型配置与连通性":"题目结构化生成服务不可用，请检查 REASONING 模型配置与连通性",Map.of("stage",stage,"reason","UNAVAILABLE"));
    }
}
