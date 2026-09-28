package com.ustb.seforge.assignment.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.assignment.domain.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ObjectiveScorerTest {
    private final ObjectiveScorer scorer=new ObjectiveScorer(new ObjectMapper());
    private AssignmentQuestion question(QuestionType type,String standard){return new AssignmentQuestion(1L,1L,null,type,"Question","[\"A\",\"B\",\"C\"]",standard,new BigDecimal("5.00"),0,null);}
    private SubmissionAnswer answer(String data){return new SubmissionAnswer(1L,1L,null,data,null);}
    @Test void exactSingleChoiceUsesIdNotLabel(){
        assertThat(scorer.score(question(QuestionType.SINGLE_CHOICE,"A"),answer("\"A\""))).isEqualByComparingTo("5");
        assertThat(scorer.score(question(QuestionType.SINGLE_CHOICE,"A"),answer("\"option A text\""))).isZero();
    }
    @Test void multipleChoiceIsOrderIndependentButRejectsPartialExtraAndDuplicateChoices(){
        var q=question(QuestionType.MULTIPLE_CHOICE,"[\"A\",\"C\"]");
        assertThat(scorer.score(q,answer("[\"C\",\"A\"]"))).isEqualByComparingTo("5");
        for(String a:new String[]{"[\"A\"]","[\"A\",\"B\",\"C\"]","[\"A\",\"A\",\"C\"]","null"})assertThat(scorer.score(q,answer(a))).isZero();
    }
    @Test void booleansAreStrictAndInvalidStandardsFailClosed(){
        assertThat(scorer.score(question(QuestionType.TRUE_FALSE,"false"),answer("false"))).isEqualByComparingTo("5");
        assertThat(scorer.score(question(QuestionType.TRUE_FALSE,"false"),answer("\"false\""))).isZero();
        assertThatThrownBy(()->scorer.score(question(QuestionType.TRUE_FALSE,"yes"),answer("true"))).hasMessageContaining("standard answer");
        assertThatThrownBy(()->scorer.score(question(QuestionType.DESIGN,"example"),answer("\"example\""))).hasMessageContaining("RULE");
    }
}
