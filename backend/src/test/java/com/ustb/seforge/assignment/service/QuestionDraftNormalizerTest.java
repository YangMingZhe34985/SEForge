package com.ustb.seforge.assignment.service;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.ustb.seforge.content.service.DocumentParserService.ParsedSection;
import org.junit.jupiter.api.Test;
import java.util.*;

class QuestionDraftNormalizerTest {
    private final ObjectMapper json=new ObjectMapper();
    private final List<ParsedSection> pages=List.of(new ParsedSection("Answer: A,C. 判断答案：错误。参考答案：保持高内聚。代码答案：return a+b; 题干内容用于验证归属 Points: 5",1,"page"));
    private JsonNode normalize(String input)throws Exception {return QuestionDraftNormalizer.normalize(json.readTree(input),pages);}
    @Test void supportsAllEightTypesMixedInOneBatch() throws Exception {
        var input=json.createObjectNode();var questions=input.putArray("questions");
        for(String type:List.of("SINGLE_CHOICE","MULTIPLE_CHOICE","TRUE_FALSE","SHORT_ANSWER","ANALYSIS","DESIGN","CODE","DOCUMENT_REPORT")) {
            var q=questions.addObject();q.put("type",type);q.put("contentMarkdown","题干内容用于验证归属");q.put("score",5);q.put("scoreEvidence","题干内容用于验证归属 Points: 5");
            if(type.endsWith("CHOICE")){q.putArray("options").add("Review").add("Ignore").add("Testing");q.put("answer",type.equals("SINGLE_CHOICE")?"A":"AC");q.put("answerEvidence","Answer: A,C");}
            else if(type.equals("TRUE_FALSE")){q.put("answer",false);q.put("answerEvidence","判断答案：错误");}
            else {q.put("referenceAnswer",type.equals("CODE")?"return a+b;":"保持高内聚。");q.put("answerEvidence",type.equals("CODE")?"代码答案：return a+b;":"参考答案：保持高内聚。");}
        }
        var normalized=QuestionDraftNormalizer.normalize(input,pages);
        var result=json.treeToValue(normalized,QuestionExtractionService.Extracted.class);
        QuestionExtractionService.validate(result,pages);
        assertThat(result.questions()).hasSize(8);
        assertThat(result.questions().get(0).correctChoiceIndexes()).containsExactly(0);
        assertThat(result.questions().get(1).correctChoiceIndexes()).containsExactly(0,2);
        assertThat(result.questions().get(2).booleanAnswer()).isEqualTo("false");
        assertThat(result.questions().get(6).referenceAnswer()).isEqualTo("return a+b;");
        assertThat(result.questions()).allSatisfy(q->assertThat(q.score()).isEqualTo("5"));
    }
    @Test void acceptsOptionObjectsChineseTypesNullIrrelevantFieldsAndWhitespaceEvidence() throws Exception {
        var result=normalize("""
          [{"questionType":"多选题","stem":"选择所有正确项","options":[{"label":"A","text":"Review"},{"label":"B","text":"Ignore"},{"label":"C","text":"Testing"}],"correctAnswer":["A","C"],"answerEvidence":"Answer: A, C","page":"1","booleanAnswer":null}]
          """);
        assertThat(result.path("questions").get(0).path("correctChoiceIndexes").toString()).isEqualTo("[0,2]");
        assertThat(result.path("questions").get(0).path("score").asText()).isEmpty();
    }
    @Test void supportsLabelMapsAndExactOptionTextWithoutConfusingWordsForLetterSets() throws Exception {
        var result=normalize("""
          {"questions":[{"type":"single_choice","prompt":"Choose","options":{"A":"Code Review","B":"Ignore"},"answer":"Code Review","answerEvidence":"Answer: A,C"}]}
          """);
        assertThat(result.path("questions").get(0).path("correctChoiceIndexes").toString()).isEqualTo("[0]");
    }
    @Test void unsupportedAnswersScoresAndRegionsBecomeWarningsWithoutInventedDefaults() throws Exception {
        var q=normalize("""
          {"questions":[{"type":"MULTIPLE_CHOICE","prompt":"Choose","choices":["A","B"],"correctChoiceIndexes":[7],"answerEvidence":"Answer: A,C","score":10,"scoreEvidence":"not in source","boundingBox":[20,30,400,500]}]}
          """).path("questions").get(0);
        assertThat(q.path("correctChoiceIndexes")).isEmpty();assertThat(q.path("score").asText()).isEmpty();
        assertThat(q.path("warning").asText()).contains("答案","分值","区域");
    }
    @Test void acceptsOnlyUnambiguousEnvelopeAndRejectsContextInjection() throws Exception {
        String target="{\"questions\":[{\"type\":\"CODE\",\"prompt\":\"Implement sum\"}]}";
        assertThat(normalize(json.writeValueAsString(Map.of("json",target))).path("questions")).hasSize(1);
        for(String input:List.of("{\"questions\":[],\"userId\":1}","{\"questions\":[{\"prompt\":\"x\",\"courseId\":2}]}","{\"questions\":[{\"prompt\":\"x\",\"page\":99}]}"))
            assertThatThrownBy(()->normalize(input)).isInstanceOf(QuestionDraftNormalizer.Invalid.class);
    }
    @Test void doesNotAssociateConflictingAnswerOrQuestionFields() {
        assertThatThrownBy(()->normalize("{\"questions\":[{\"prompt\":\"x\",\"stem\":\"y\"}]}"))
                .isInstanceOf(QuestionDraftNormalizer.Invalid.class);
    }
    @Test void doesNotBorrowAnotherQuestionsScore() throws Exception {
        var q=normalize("{\"questions\":[{\"type\":\"DOCUMENT_REPORT\",\"contentMarkdown\":\"提交测试报告\",\"score\":5,\"scoreEvidence\":\"Points: 5\"}]}").path("questions").get(0);
        assertThat(q.path("score").asText()).isEmpty();
        assertThat(q.path("warning").asText()).contains("分值");
    }
}
