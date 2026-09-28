package com.ustb.seforge.assignment.service;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.ustb.seforge.identity.api.CreateUserRequest;
import com.ustb.seforge.identity.domain.*;
import com.ustb.seforge.identity.service.IdentityService;
import com.ustb.seforge.content.infrastructure.*;
import com.ustb.seforge.config.SEForgeProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.junit.jupiter.Container;

/** Isolated real HTTP, MySQL/Flyway, MinIO, production Parser/Vision/Gateway; controlled HTTP AI. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @Testcontainers(disabledWithoutDocker=true)
@Import(QuestionImportHttpIntegrationTest.Storage.class) @DirtiesContext
class QuestionImportHttpIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4.4").withCommand("--log-bin-trust-function-creators=1").withDatabaseName("imports").withUsername("imports").withPassword("isolated-import-test");
    @Container static final GenericContainer<?> MINIO=new GenericContainer<>("quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z").withEnv("MINIO_ROOT_USER","import-test").withEnv("MINIO_ROOT_PASSWORD","isolated-import-test").withCommand("server","/data").withExposedPorts(9000).waitingFor(Wait.forHttp("/minio/health/live"));
    static final ObjectMapper JSON=new ObjectMapper();
    static final AtomicInteger CALLS=new AtomicInteger(), IMAGES=new AtomicInteger();
    static final String SOURCE="1. SINGLE_CHOICE Choose. A. Circle B. Square C. Line D. Dot. Answer: A. Points: 5\n2. Explain the diagram.";
    static final HttpServer PROVIDER=provider();
    static volatile boolean invalid;
    @TestConfiguration static class Storage {@Bean @Primary ObjectStorage realStorage(SEForgeProperties p){return new MinioObjectStorage(p);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        r.add("spring.flyway.enabled",()->true);r.add("spring.jpa.hibernate.ddl-auto",()->"validate");r.add("seforge.jobs.enabled",()->false);
        r.add("seforge.storage.endpoint",()->"http://"+MINIO.getHost()+":"+MINIO.getMappedPort(9000));r.add("seforge.storage.access-key",()->"import-test");r.add("seforge.storage.secret-key",()->"isolated-import-test");r.add("seforge.storage.bucket",()->"imports");
        r.add("seforge.ai.enabled",()->true);r.add("seforge.ai.deepseek-api-key",()->"");r.add("seforge.ai.dashscope-api-key",()->"isolated-stub-not-secret");r.add("seforge.ai.dashscope-base-url",()->"http://127.0.0.1:"+PROVIDER.getAddress().getPort()+"/v1");r.add("seforge.ai.max-retries",()->0);r.add("seforge.ai.vision-model",()->"vision-only");
    }
    @Autowired IdentityService identity; @Autowired JdbcTemplate jdbc; @Autowired GradeSuggestionService suggestions;
    @Autowired com.ustb.seforge.assignment.repository.SubmissionRepository submissions;
    @LocalServerPort int port;
    @AfterAll static void close(){PROVIDER.stop(0);}

    @Test void importConfirmationMediaAndGradingAreIsolatedAndIdempotent()throws Exception {
        long teacher=user("import-teacher",AccountType.TEACHER,null),other=user("import-other",AccountType.TEACHER,null),student=user("import-student",AccountType.STUDENT,"IMPORT-001");
        jdbc.update("insert into semesters(id,code,name,starts_on,ends_on,status) values(701,'import','import','2026-01-01','2026-12-31','ACTIVE')");
        for(long course:List.of(701L,702L))jdbc.update("insert into courses(id,code,name,semester_id,owner_id) values(?,?,?,701,?)",course,"import-"+course,"Import "+course,course==701?teacher:other);
        jdbc.update("insert into course_members(course_id,user_id,role) values(701,?,'TEACHER')",teacher);jdbc.update("insert into course_members(course_id,user_id,role) values(702,?,'TEACHER')",other);jdbc.update("insert into course_members(course_id,user_id,role) values(701,?,'STUDENT')",student);
        var t=login("import-teacher","TEACHER");var o=login("import-other","TEACHER");var s=login("IMPORT-001","STUDENT");
        long assignment=data(send(t,"POST","/api/v1/courses/701/assignments",Map.of("title","Imported assignment"))).path("id").asLong();String path="/api/v1/assignments/"+assignment;
        long second=data(send(o,"POST","/api/v1/courses/702/assignments",Map.of("title","Other assignment"))).path("id").asLong();
        long pdf=upload(t,path,"IMPORT","exam.pdf","application/pdf",pdf());
        var draft=data(send(t,"POST",path+"/question-imports?sourceFile="+pdf,null));
        assertThat(draft.path("questions").size()).isEqualTo(2);assertThat(IMAGES.get()).isZero();
        assertThat(draft.path("questions").get(1).path("score").isNull()).isTrue();assertThat(draft.path("questions").get(1).path("type").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from assignment_question where assignment_id=?",Integer.class,assignment)).isZero();
        assertThat(send(o,"GET",path+"/question-imports/"+draft.path("id").asLong(),null).statusCode()).isEqualTo(403);
        assertThat(send(o,"POST","/api/v1/assignments/"+second+"/question-imports?sourceFile="+pdf,null).statusCode()).isEqualTo(404);
        assertThat(send(o,"GET","/api/v1/assignments/"+second+"/question-imports/"+draft.path("id").asLong(),null).statusCode()).isEqualTo(404);
        var q=draft.path("questions").get(0);var choices=JSON.convertValue(q.path("choices"),List.class);
        var input=Map.of("type","SINGLE_CHOICE","prompt","Teacher edited: Choose a shape","points",5,"orderIndex",0,"options",List.of(q.path("choices").get(0).path("id").asText(),q.path("choices").get(1).path("id").asText(),q.path("choices").get(2).path("id").asText(),q.path("choices").get(3).path("id").asText()),"config",Map.of("schemaVersion",1,"choices",choices,"answerSpec",Map.of("correct",q.path("correctAnswer").asText()),"gradingMode","RULE"));
        var confirmation=Map.of("questions",List.of(Map.of("draftKey",q.path("draftKey").asText(),"question",input)));
        String confirm=path+"/question-imports/"+draft.path("id").asLong()+"/confirm";
        var concurrent=List.of(CompletableFuture.supplyAsync(()->unchecked(t,"POST",confirm,confirmation)),CompletableFuture.supplyAsync(()->unchecked(t,"POST",confirm,confirmation)));
        var first=data(concurrent.getFirst().join());assertThat(data(concurrent.getLast().join())).isEqualTo(first);long rule=first.get(0).path("id").asLong();
        assertThat(jdbc.queryForObject("select count(*) from assignment_question where assignment_id=?",Integer.class,assignment)).isEqualTo(1);
        assertThat(data(send(t,"POST",path+"/question-imports?sourceFile="+pdf,null)).path("confirmed").asBoolean()).isTrue();
        long md=upload(t,path,"IMPORT","exam.md","text/markdown",SOURCE.getBytes(java.nio.charset.StandardCharsets.UTF_8));assertThat(data(send(t,"POST",path+"/question-imports?sourceFile="+md,null)).path("questions").size()).isEqualTo(2);
        var beforeReparse=data(send(t,"POST",path+"/question-imports?sourceFile="+md,null));
        var afterReparse=data(send(t,"POST",path+"/question-imports?sourceFile="+md+"&reparse=true",null));
        assertThat(afterReparse.path("id")).isEqualTo(beforeReparse.path("id"));
        assertThat(afterReparse.path("questions").get(0).path("draftKey")).isNotEqualTo(beforeReparse.path("questions").get(0).path("draftKey"));
        assertThat(send(t,"POST",path+"/question-imports/"+afterReparse.path("id").asLong()+"/confirm",Map.of("questions",List.of(Map.of("draftKey",beforeReparse.path("questions").get(0).path("draftKey").asText(),"question",input)))).statusCode()).isEqualTo(400);
        long invalidFile=upload(t,path,"IMPORT","invalid.md","text/markdown",SOURCE.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        invalid=true;
        try {var failed=send(t,"POST",path+"/question-imports?sourceFile="+invalidFile,null);assertThat(failed.statusCode()).isEqualTo(502);assertThat(JSON.readTree(failed.body()).path("code").asText()).isEqualTo("QUESTION_EXTRACTION_INVALID_OUTPUT");}
        finally {invalid=false;}
        assertThat(jdbc.queryForObject("select count(*) from question_import_draft where source_media_id=?",Integer.class,invalidFile)).isZero();
        byte[] png=image();long imageSource=upload(t,path,"IMPORT","exam.png","image/png",png);assertThat(data(send(t,"POST",path+"/question-imports?sourceFile="+imageSource,null)).path("questions").size()).isEqualTo(2);assertThat(IMAGES.get()).isEqualTo(1);
        long page1=upload(t,path,"IMPORT","page1.png","image/png",png),page2=upload(t,path,"IMPORT","page2.png","image/png",png),page3=upload(t,path,"IMPORT","page3.png","image/png",png);
        String batchPath=path+"/question-imports?sourceFile="+page1+","+page2+","+page3;
        int imagesBefore=IMAGES.get();
        var batch=data(send(t,"POST",batchPath,null));
        assertThat(IMAGES.get()).isEqualTo(imagesBefore+3);
        assertThat(batch.path("sources")).extracting(n->n.path("id").asLong()).containsExactly(page1,page2,page3);
        assertThat(batch.path("questions")).extracting(n->n.path("sourceFile").asLong()).containsExactly(page1,page2,page3);
        assertThat(batch.path("questions")).allMatch(n->n.path("sourcePage").asInt()==1);
        int callsBefore=CALLS.get();
        assertThat(data(send(t,"POST",batchPath,null)).path("id")).isEqualTo(batch.path("id"));
        assertThat(CALLS.get()).isEqualTo(callsBefore);
        var reparsed=data(send(t,"POST",batchPath+"&reparse=true&reparseFile="+page2,null));
        assertThat(IMAGES.get()).isEqualTo(imagesBefore+4);
        assertThat(reparsed.path("questions").get(0).path("draftKey")).isEqualTo(batch.path("questions").get(0).path("draftKey"));
        assertThat(reparsed.path("questions").get(1).path("draftKey")).isNotEqualTo(batch.path("questions").get(1).path("draftKey"));
        assertThat(reparsed.path("questions").get(2).path("draftKey")).isEqualTo(batch.path("questions").get(2).path("draftKey"));
        assertThat(send(t,"POST",batchPath+","+imageSource,null).statusCode()).isEqualTo(400);
        assertThat(send(t,"POST",path+"/question-imports?sourceFile="+page1+","+page1,null).statusCode()).isEqualTo(400);
        assertThat(send(t,"POST",path+"/question-imports?sourceFile="+pdf+","+page1,null).statusCode()).isEqualTo(400);
        assertThat(send(t,"POST",batchPath+"&reparseFile="+imageSource,null).statusCode()).isEqualTo(400);
        assertThat(send(s,"POST",batchPath,null).statusCode()).isEqualTo(403);
        assertThat(send(o,"POST","/api/v1/assignments/"+second+"/question-imports?sourceFile="+page1+","+page2,null).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select count(*) from assignment_question where assignment_id=?",Integer.class,assignment)).isEqualTo(1);
        // Teacher selects one item from the batch in a separate draft assignment, with idempotent confirmation.
        long batchAssignment=data(send(t,"POST","/api/v1/courses/701/assignments",Map.of("title","Multi image confirmation"))).path("id").asLong();
        String bp="/api/v1/assignments/"+batchAssignment;
        long b1=upload(t,bp,"IMPORT","a.png","image/png",png),b2=upload(t,bp,"IMPORT","b.png","image/png",png);
        var bd=data(send(t,"POST",bp+"/question-imports?sourceFile="+b2+","+b1,null));
        assertThat(bd.path("questions").get(0).path("sourceFile").asLong()).isEqualTo(b2);
        var selected=Map.of("questions",List.of(Map.of("draftKey",bd.path("questions").get(1).path("draftKey").asText(),"question",Map.of("type","SHORT_ANSWER","prompt","Teacher edited multi-image item","points",4,"orderIndex",0,"config",Map.of("gradingMode","MANUAL")))));
        var confirmed=data(send(t,"POST",bp+"/question-imports/"+bd.path("id").asText()+"/confirm",selected));
        assertThat(confirmed).hasSize(1);
        assertThat(data(send(t,"POST",bp+"/question-imports/"+bd.path("id").asText()+"/confirm",selected))).isEqualTo(confirmed);
        long reference=upload(t,path,"REFERENCE_ANSWER","answer.png","image/png",png);assertThat(data(send(t,"POST",path+"/reference-markdown-drafts?sourceFile="+reference,null)).path("markdown").asText()).contains("Choose");
        long reference2=upload(t,path,"REFERENCE_ANSWER","answer2.png","image/png",png);
        long content=upload(t,path,"QUESTION_CONTENT","question.png","image/png",png);
        assertThat(send(t,"POST",path+"/questions",Map.of("type","DESIGN","prompt","attack","points",5,"orderIndex",1,"config",Map.of("assetIds",List.of(reference)))).statusCode()).isEqualTo(404);
        var manual=data(send(t,"POST",path+"/questions",Map.of("type","DESIGN","prompt","","points",5,"orderIndex",1,"config",Map.of("schemaVersion",1,"assetIds",List.of(content),"answerSpec",Map.of("assetIds",List.of(reference,reference2))))));
        assertThat(manual.path("config").path("gradingMode").asText()).isEqualTo("MANUAL");long manualId=manual.path("id").asLong();
        assertThat(send(o,"PUT","/api/v1/assignments/"+second+"/questions/"+manualId,Map.of("type","DESIGN","prompt","attack","points",1,"orderIndex",0)).statusCode()).isEqualTo(404);
        data(send(t,"PUT",path+"/rubric",Map.of("title","Teacher rubric","totalScore",10,"status","DRAFT")));
        long ruleItem=data(send(t,"POST",path+"/rubric/items",Map.of("questionId",rule,"title","Rule","maxScore",5,"orderIndex",0))).path("id").asLong();
        long manualItem=data(send(t,"POST",path+"/rubric/items",Map.of("questionId",manualId,"title","Manual","maxScore",5,"orderIndex",1))).path("id").asLong();
        data(send(t,"PUT",path+"/rubric",Map.of("title","Teacher rubric","totalScore",10,"status","PUBLISHED")));data(send(t,"POST",path+"/transition",Map.of("status","PUBLISHED")));
        assertThat(send(s,"GET",path+"/media/"+pdf+"/download",null).statusCode()).isEqualTo(404);assertThat(send(s,"GET",path+"/media/"+reference,null).statusCode()).isEqualTo(404);
        for(long ref:List.of(reference,reference2)) {
            assertThat(send(s,"GET",path+"/media/"+ref+"/download",null).statusCode()).isEqualTo(404);
            assertThat(send(t,"GET",path+"/media/"+ref+"/download",null).statusCode()).isEqualTo(200);
        }
        assertThat(data(send(s,"GET",path,null)).path("questions").get(1).path("config").path("assetIds")).extracting(JsonNode::asLong).containsExactly(content);
        assertThat(data(send(s,"GET",path,null)).path("questions").get(1).path("config").has("answerSpec")).isFalse();
        assertThat(send(s,"GET",path+"/media/"+content+"/download",null).statusCode()).isEqualTo(200);
        // Even stale/corrupt content bindings cannot expose a reference image in student DTOs or downloads.
        jdbc.update("update assignment_question set config=JSON_SET(config,'$.assetIds',JSON_ARRAY(?,?)) where id=?",content,reference,manualId);
        assertThat(data(send(s,"GET",path,null)).path("questions").get(1).path("config").path("assetIds")).extracting(JsonNode::asLong).containsExactly(content);
        assertThat(send(s,"GET",path+"/media/"+reference+"/download",null).statusCode()).isEqualTo(404);
        jdbc.update("update assignment_question set config=JSON_SET(config,'$.assetIds',JSON_ARRAY(?)) where id=?",content,manualId);
        int before=CALLS.get();
        var submitted=data(send(s,"POST",path+"/submissions",Map.of("submissionKey",UUID.randomUUID().toString(),"answers",List.of(Map.of("questionId",rule,"answer",q.path("correctAnswer").asText()),Map.of("questionId",manualId,"answer","Teacher must assess diagram")))));long submission=submitted.path("id").asLong();
        assertThat(suggestions.manualItems(submissions.findById(submission).orElseThrow())).containsExactly(manualItem);assertThat(CALLS.get()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from feedback where source='RULE'",Integer.class)).isEqualTo(1);
        data(send(t,"POST","/api/v1/submissions/"+submission+"/grade/confirm",Map.of("score",9,"reason","Teacher completed manual assessment","rubricItems",List.of(Map.of("rubricItemId",ruleItem,"score",5),Map.of("rubricItemId",manualItem,"score",4)))));
        assertThat(jdbc.queryForObject("select status from grade where submission_id=?",String.class,submission)).isEqualTo("CONFIRMED");
        assertThat(CALLS.get()).isEqualTo(before);
    }
    private long user(String name,AccountType type,String number){return identity.createUser(new CreateUserRequest(name+"@example.invalid",name,"ImportTestPassword!",name,type,Set.of(GlobalRole.USER),number)).id();}
    private record Session(HttpClient client,String header,String token){}
    private Session login(String name,String portal)throws Exception {var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();var csrf=JSON.readTree(client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).path("data");var session=new Session(client,csrf.path("headerName").asText(),csrf.path("token").asText());data(send(session,"POST","/api/v1/auth/login",Map.of("identifier",name,"portal",portal,"password","ImportTestPassword!")));csrf=data(send(session,"GET","/api/v1/auth/csrf",null));return new Session(client,csrf.path("headerName").asText(),csrf.path("token").asText());}
    private HttpResponse<String> send(Session s,String method,String path,Object body)throws Exception {return s.client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(45)).header(s.header,s.token).header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());}
    private HttpResponse<String> unchecked(Session s,String method,String path,Object body){try{return send(s,method,path,body);}catch(Exception e){throw new IllegalStateException(e);}}
    private JsonNode data(HttpResponse<String> response)throws Exception {assertThat(response.statusCode()).as(response.body()).isBetween(200,299);return JSON.readTree(response.body()).path("data");}
    private long upload(Session s,String path,String purpose,String name,String mime,byte[] bytes)throws Exception {String boundary="ImportTestBoundary";var output=new ByteArrayOutputStream();output.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"purpose\"\r\n\r\n"+purpose+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes());output.write(bytes);output.write(("\r\n--"+boundary+"--\r\n").getBytes());return data(s.client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path+"/media")).header(s.header,s.token).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(output.toByteArray())).build(),HttpResponse.BodyHandlers.ofString())).path("id").asLong();}
    private byte[] pdf()throws Exception {try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){var page=new PDPage();doc.addPage(page);try(var stream=new PDPageContentStream(doc,page)){stream.beginText();stream.setFont(PDType1Font.HELVETICA,10);stream.newLineAtOffset(40,720);stream.showText(SOURCE.replace('\n',' '));stream.endText();}doc.save(out);return out.toByteArray();}}
    private byte[] image()throws Exception {var img=new java.awt.image.BufferedImage(900,150,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=img.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,900,150);g.setColor(java.awt.Color.BLACK);g.drawString(SOURCE,10,40);g.dispose();var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(img,"png",out);return out.toByteArray();}
    private static HttpServer provider(){try {var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/v1/chat/completions",exchange->{CALLS.incrementAndGet();var request=JSON.readTree(exchange.getRequestBody());var messages=request.path("messages");boolean image=messages.path(messages.size()-1).path("content").isArray();String answer;
        if(image){IMAGES.incrementAndGet();answer=SOURCE;}else {var first=new QuestionExtractionService.ExtractedQuestion("SINGLE_CHOICE","Choose",List.of("Circle","Square","Line","Dot"),List.of(0),"","","Answer: A","5","Points: 5",1,List.of(),"");var second=new QuestionExtractionService.ExtractedQuestion("UNKNOWN","Explain diagram",List.of(),List.of(),"","","","","",1,List.of(),"Teacher must confirm type and score");answer=JSON.writeValueAsString(Map.of("json",JSON.writeValueAsString(new QuestionExtractionService.Extracted(List.of(first,second)))));}
        if(!image && messages.path(messages.size()-1).path("content").asText().contains("[PAGE 2]")) {
            int pages=messages.path(messages.size()-1).path("content").asText().contains("[PAGE 3]")?3:2;
            var batch=new ArrayList<QuestionExtractionService.ExtractedQuestion>();
            for(int page=1;page<=pages;page++)batch.add(new QuestionExtractionService.ExtractedQuestion("UNKNOWN","Explain diagram",List.of(),List.of(),"","","","","",page,List.of(),"Teacher must confirm"));
            answer=JSON.writeValueAsString(Map.of("json",JSON.writeValueAsString(new QuestionExtractionService.Extracted(batch))));
        }
        if(invalid)answer=JSON.writeValueAsString(Map.of("json","{\"questions\":[],\"userId\":999}"));
        if(request.path("stream").asBoolean()) {
            answer=JSON.readTree(answer).path("json").asText();
            String event=JSON.writeValueAsString(Map.of("id","import-stub","object","chat.completion.chunk","choices",List.of(Map.of("index",0,"delta",Map.of("content",answer),"finish_reason","stop"))));
            byte[] body=("data: "+event+"\n\ndata: [DONE]\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();return;
        }
        byte[] response=JSON.writeValueAsBytes(Map.of("id","import-stub","object","chat.completion","choices",List.of(Map.of("index",0,"message",Map.of("role","assistant","content",answer),"finish_reason","stop")),"usage",Map.of("prompt_tokens",20,"completion_tokens",20,"total_tokens",40)));exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();});server.start();return server;}catch(IOException e){throw new IllegalStateException(e);}}
}
