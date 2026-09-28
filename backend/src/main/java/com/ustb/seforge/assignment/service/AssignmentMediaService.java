package com.ustb.seforge.assignment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ustb.seforge.assignment.domain.*;
import com.ustb.seforge.assignment.repository.*;
import com.ustb.seforge.common.exception.*;
import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.content.infrastructure.ObjectStorage;
import com.ustb.seforge.course.service.CourseAccessService;
import com.ustb.seforge.course.repository.CourseRepository;
import com.ustb.seforge.identity.repository.UserRepository;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssignmentMediaService {
    private static final Map<String,String> TYPES = Map.of("png","image/png", "jpg","image/jpeg",
            "jpeg","image/jpeg", "pdf","application/pdf", "docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "md","text/markdown", "txt","text/plain");
    private final AssignmentMediaRepository media;
    private final AssignmentRepository assignments;
    private final AssignmentQuestionRepository questions;
    private final CourseAccessService access;
    private final AssignmentAuthorizationService authorization;
    private final ObjectStorage storage;
    private final UserRepository users;
    private final CourseRepository courses;
    private final SEForgeProperties properties;
    private final SubmissionAnswerRepository answers;
    public AssignmentMediaService(AssignmentMediaRepository media, AssignmentRepository assignments,
            AssignmentQuestionRepository questions, CourseAccessService access, AssignmentAuthorizationService authorization,
            ObjectStorage storage, UserRepository users, CourseRepository courses, SEForgeProperties properties,
            SubmissionAnswerRepository answers) {
        this.media=media; this.assignments=assignments; this.questions=questions; this.access=access;
        this.authorization=authorization; this.storage=storage; this.users=users; this.courses=courses;
        this.properties=properties; this.answers=answers;
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public AssignmentMedia upload(Long assignmentId, Long userId, AssignmentMedia.Purpose purpose,
            Submission draft, Long questionId, MultipartFile file) {
        Assignment assignment=assignments.findByIdForUpdate(assignmentId).orElseThrow(this::missing);
        if(purpose==AssignmentMedia.Purpose.ANSWER) {
            authorization.requireStudent(assignment,userId);
            if(draft==null || !draft.getAssignmentId().equals(assignmentId) || !draft.getUserId().equals(userId)) throw missing();
            draft.requireDraft();
            AssignmentQuestion question=questions.findByIdAndAssignmentId(questionId,assignmentId).orElseThrow(this::missing);
            if(question.getQuestionType().objective()) throw QuestionContent.invalid("Objective answers do not accept attachments");
            JsonNode allowed=QuestionContent.config(question).path("answerSpec").path("allowedFileTypes");
            if(question.getQuestionType()==QuestionType.DOCUMENT_REPORT && allowed.isArray()
                    && java.util.stream.StreamSupport.stream(allowed.spliterator(),false).noneMatch(n->n.asText().equals(extension(file.getOriginalFilename()))))
                throw QuestionContent.invalid("This file type is not allowed for this report");
        } else { access.requireTeachingStaff(assignment.getCourseId(),userId); assignment.requireDraft(); }
        byte[] bytes;
        try { bytes=validate(file); } catch(IOException e) {throw QuestionContent.invalid("Cannot read uploaded file");}
        users.findByIdForUpdate(userId).orElseThrow(this::missing);
        courses.findForUpdate(assignment.getCourseId()).orElseThrow(this::missing);
        if(media.bytesByOwner(userId)+answers.sumAttachmentBytesByUserId(userId)+bytes.length > properties.getStorage().getAttachmentUserQuotaBytes()
                || media.bytesByCourse(assignment.getCourseId())+answers.sumAttachmentBytesByCourseId(assignment.getCourseId())+bytes.length > properties.getStorage().getAttachmentCourseQuotaBytes())
            throw QuestionContent.invalid("Attachment storage quota exceeded");
        String name=file.getOriginalFilename().replaceAll("[^\\p{L}\\p{N}._-]","_");
        if(name.length()>180) name=name.substring(name.length()-180);
        String type=TYPES.get(extension(name));
        String key="courses/"+assignment.getCourseId()+"/assignment-media/"+UUID.randomUUID()+"/"+name;
        try {storage.put(key,new ByteArrayInputStream(bytes),bytes.length,type);}
        catch(IOException e){throw new AppException(ErrorCode.INFRASTRUCTURE_UNAVAILABLE,"Assignment storage unavailable");}
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{storage.delete(key);}catch(Exception ignored){}}
        });
        return media.save(new AssignmentMedia(assignmentId,assignment.getCourseId(),userId,
                purpose==AssignmentMedia.Purpose.ANSWER?questionId:null,draft==null?null:draft.getId(),purpose.canonical(),key,name,type,bytes.length));
    }
    public AssignmentMedia bound(Long id,Long assignmentId,AssignmentMedia.Purpose purpose,Long submissionId,Long questionId){
        return media.findById(id).filter(m->m.getAssignmentId().equals(assignmentId) && m.getPurpose().canonical()==purpose.canonical()
                && (purpose!=AssignmentMedia.Purpose.ANSWER || Objects.equals(m.getSubmissionId(),submissionId)&&Objects.equals(m.getQuestionId(),questionId)))
                .orElseThrow(this::missing);
    }
    public void validateAnswer(Submission submission,Long questionId,JsonNode answer){
        if(answer==null || !answer.isObject()) return;
        if(answer.size()>2 || !answer.has("text") || !answer.path("text").isTextual() || !answer.has("assetIds")) throw QuestionContent.invalid("Rich answer requires text and assetIds only");
        for(Long id:QuestionContent.ids(answer.path("assetIds"))) bound(id,submission.getAssignmentId(),AssignmentMedia.Purpose.ANSWER,submission.getId(),questionId);
    }
    @Transactional(readOnly=true)
    public AssignmentMedia download(Long assignmentId,Long id,Long userId){
        Assignment assignment=assignments.findById(assignmentId).orElseThrow(this::missing);
        authorization.requireVisible(assignment,userId);
        AssignmentMedia value=media.findById(id).filter(m->m.getAssignmentId().equals(assignmentId)).orElseThrow(this::missing);
        if(access.isTeachingStaff(assignment.getCourseId(),userId))return value;
        if(value.getPurpose().canonical()==AssignmentMedia.Purpose.REFERENCE_ANSWER || value.getPurpose()==AssignmentMedia.Purpose.IMPORT)throw missing();
        if(value.getPurpose()==AssignmentMedia.Purpose.ANSWER && !value.getOwnerId().equals(userId))throw missing();
        if(value.getPurpose().canonical()==AssignmentMedia.Purpose.QUESTION_CONTENT && !publicContentIds(assignmentId,List.of(id)).contains(id))throw missing();
        return value;
    }
    /** Fail closed for stale/corrupt configs: a reference binding always wins over a content binding. */
    public List<Long> publicContentIds(Long assignmentId,List<Long> candidates) {
        var all=questions.findAllByAssignmentIdOrderBySortOrderAscIdAsc(assignmentId);
        var references=all.stream().flatMap(q->QuestionContent.ids(QuestionContent.config(q).path("answerSpec").path("assetIds")).stream()).collect(java.util.stream.Collectors.toSet());
        var content=all.stream().flatMap(q->QuestionContent.ids(QuestionContent.config(q).path("assetIds")).stream()).collect(java.util.stream.Collectors.toSet());
        return candidates.stream().filter(id->!references.contains(id)&&content.contains(id)).filter(id->media.findById(id)
                .filter(m->m.getAssignmentId().equals(assignmentId)&&m.getPurpose().canonical()==AssignmentMedia.Purpose.QUESTION_CONTENT).isPresent()).toList();
    }
    public byte[] read(AssignmentMedia value){try(InputStream in=storage.open(value.getObjectKey())){return in.readNBytes(20*1024*1024+1);}
        catch(IOException e){throw new AppException(ErrorCode.INFRASTRUCTURE_UNAVAILABLE,"Assignment attachment storage is unavailable");}}
    public static byte[] validate(MultipartFile file)throws IOException{
        if(file==null||file.isEmpty()||file.getSize()>20*1024*1024)throw QuestionContent.invalid("File must be between 1 byte and 20 MB");
        String ext=extension(file.getOriginalFilename()); String type=TYPES.get(ext);
        if(type==null || file.getContentType()==null || !(type.equals(file.getContentType())
                || ext.equals("md")&&file.getContentType().equals("text/plain")))throw QuestionContent.invalid("Unsupported file extension or MIME type");
        byte[] bytes=file.getBytes();
        if(type.startsWith("image/")) {
            try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
                var readers=ImageIO.getImageReaders(input); if(!readers.hasNext())throw QuestionContent.invalid("Invalid image");
                var reader=readers.next();try{reader.setInput(input);String format=reader.getFormatName();
                    if(!(ext.equals("png")?format.equalsIgnoreCase("png"):format.equalsIgnoreCase("jpeg")))throw QuestionContent.invalid("Image signature mismatch");
                    if((long)reader.getWidth(0)*reader.getHeight(0)>20_000_000)throw QuestionContent.invalid("Image exceeds 20 megapixels");
                    if(reader.read(0)==null)throw QuestionContent.invalid("Invalid image");
                }finally{reader.dispose();}
            }
        } else if(ext.equals("pdf") && (bytes.length<5 || !new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")))throw QuestionContent.invalid("Invalid PDF signature");
        else if(ext.equals("docx")){
            boolean document=false; long total=0;int count=0;
            try(var zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))){
                java.util.zip.ZipEntry entry;byte[] buffer=new byte[8192];
                while((entry=zip.getNextEntry())!=null){
                    if(++count>1000||entry.getName().contains("..")||entry.getName().contains("\\")||entry.getName().startsWith("/")||entry.getName().endsWith(".zip"))throw QuestionContent.invalid("Unsafe DOCX archive");
                    document|=entry.getName().equals("word/document.xml");int n;
                    while((n=zip.read(buffer))!=-1){total+=n;if(total>50*1024*1024||total>bytes.length*100L)throw QuestionContent.invalid("DOCX expands beyond safe limit");}
                }
            } if(!document)throw QuestionContent.invalid("Invalid DOCX");
        }
        return bytes;
    }
    private static String extension(String name){if(name==null)return "";int dot=name.lastIndexOf('.');return dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);}
    private AppException missing(){return new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Assignment attachment not found");}
}
