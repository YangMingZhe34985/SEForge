package com.ustb.seforge.assignment.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import com.ustb.seforge.content.service.DocumentParserService.ParsedSection;
import com.ustb.seforge.assignment.service.QuestionExtractionService.*;
class QuestionExtractionValidationTest {
    @org.junit.jupiter.api.Test void reportsExtractionTimeoutSeparatelyFromVisionConfiguration() {
        var error=QuestionExtractionService.diagnostic(new com.ustb.seforge.ai.application.AiUnavailableException("provider",new java.util.concurrent.TimeoutException()),"QUESTION_EXTRACTION");
        assertThat(error.getErrorCode()).isEqualTo(com.ustb.seforge.common.exception.ErrorCode.QUESTION_EXTRACTION_TIMEOUT);
        assertThat(error.getMessage()).contains("结构化生成超时").doesNotContain("VISION_MODEL");
    }
    private final List<ParsedSection> pages=List.of(new ParsedSection("Choose a shape. Answer: A. Points: 5",1,"Page 1"));
    private Extracted sample(String score,String proof,int page,List<Integer> correct,List<Double> box){return new Extracted(List.of(new ExtractedQuestion("SINGLE_CHOICE","Choose",List.of("Circle","Square"),correct,"","","Answer: A",score,proof,page,box,"")));}
    @Test void preservesUnknownAnswersAndScores(){assertThatCode(()->QuestionExtractionService.validate(sample("","",1,List.of(),List.of()),pages)).doesNotThrowAnyException();}
    @Test void requiresLiteralScoreEvidence(){assertThatThrownBy(()->QuestionExtractionService.validate(sample("5","invented",1,List.of(),List.of()),pages)).isInstanceOf(IllegalArgumentException.class);}
    @Test void rejectsBadPageChoiceAndRegion(){
        assertThatThrownBy(()->QuestionExtractionService.validate(sample("","",2,List.of(),List.of()),pages)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->QuestionExtractionService.validate(sample("","",1,List.of(5),List.of()),pages)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->QuestionExtractionService.validate(sample("","",1,List.of(),List.of(.8,.1,.2,.9)),pages)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->QuestionExtractionService.validate(sample("","",1,List.of(0,1),List.of()),pages)).isInstanceOf(IllegalArgumentException.class);
    }
}
