package com.ustb.seforge.review.service;

import com.ustb.seforge.ai.application.PromptCatalog;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ChineseReviewPromptTest {
    @Test void allReviewPromptsRequireChineseWithoutTranslatingSchemaOrSonarEvidence() {
        var catalog=new PromptCatalog();
        for(String name:java.util.List.of("assignment-review","document-review","code-review")) {
            String version=name.equals("code-review")?"v3":"v2";
            assertThat(catalog.load(name,version).text()).contains("简体中文","JSON","字段名");
        }
        assertThat(catalog.load("assignment-review","v2").text()).contains("summary","evidence","issues","feedback","advisory");
        assertThat(catalog.load("code-review","v3").text()).contains("原样保留","without executing student code");
    }
}
