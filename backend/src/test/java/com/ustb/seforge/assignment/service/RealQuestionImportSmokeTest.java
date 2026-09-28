package com.ustb.seforge.assignment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.ai.application.*;
import com.ustb.seforge.ai.domain.AiTrace;
import com.ustb.seforge.ai.infrastructure.*;
import com.ustb.seforge.config.SEForgeProperties;
import com.ustb.seforge.content.service.DocumentParserService.ParsedSection;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in cloud smoke using only a generated synthetic exam image; no teaching data or database writes. */
@EnabledIfEnvironmentVariable(named="SEFORGE_REAL_IMPORT_SMOKE", matches="true")
class RealQuestionImportSmokeTest {
    @Test void syntheticImageReachesStrictQuestionDraftThroughConfiguredDashscope() throws Exception {
        var properties=new SEForgeProperties();var settings=properties.getAi();
        settings.setEnabled(true);settings.setDashscopeApiKey(System.getenv("DASHSCOPE_API_KEY"));
        settings.setDashscopeBaseUrl(System.getenv().getOrDefault("DASHSCOPE_BASE_URL","https://dashscope.aliyuncs.com/compatible-mode/v1"));
        settings.setVisionModel(System.getenv().getOrDefault("SEFORGE_AI_VISION_MODEL","qwen3.8-max"));
        settings.setFallbackModel(System.getenv().getOrDefault("SEFORGE_FALLBACK_MODEL","qwen3.8-flash"));
        settings.setMaxRetries(0);settings.setTimeout(Duration.ofSeconds(90));
        var registry=new ModelRegistry(properties);var router=new ModelRouter(registry);
        var traces=mock(AiTraceService.class);var trace=mock(AiTrace.class);when(trace.getId()).thenReturn(1L);
        when(traces.begin(any(),anyString(),anyString())).thenReturn(trace);
        var image=new java.awt.image.BufferedImage(1000,240,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,1000,240);g.setColor(java.awt.Color.BLACK);
        g.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,24));
        String[] lines={"1. Single choice: Which practice improves software quality?","A. Code review   B. Ignore tests   C. Hide defects   D. Skip requirements","Answer: A. Points: 5.","2. Short answer: Explain unit testing. No answer or points supplied."};
        for(int i=0;i<lines.length;i++)g.drawString(lines[i],20,40+i*48);g.dispose();
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        long start=System.nanoTime();
        var normalized=new VisionContentProcessor(router,traces,settings.getVisionModel()).normalize(1L,1L,1L,bytes.toByteArray(),"image/png");
        long visionMs=(System.nanoTime()-start)/1_000_000;
        var sections=List.of(new ParsedSection(normalized.text(),1,"Synthetic image"));
        var template=new PromptCatalog().load("question-extraction","v2");
        var gateway=new AiGateway(router,traces,new ObjectMapper(),new AiServiceFactory(registry));
        try {
            var result=gateway.completeJsonStreaming(new AiRequest(ModelCapability.REASONING,1L,1L,template.identifier(),template.text(),"[PAGE 1]\n"+normalized.text()),Duration.ofSeconds(300),QuestionExtractionService.Extracted.class,v->QuestionExtractionService.validate(v,sections),n->QuestionDraftNormalizer.normalize(n,sections));
            assertThat(result.questions()).hasSize(2);
            assertThat(result.questions().get(0).correctChoiceIndexes()).containsExactly(0);
            assertThat(result.questions().get(1).score()).isEmpty();
            assertThat(result.questions().get(1).referenceAnswer()).isEmpty();
            System.out.println("REAL_IMPORT_SMOKE PASS visionMs="+visionMs+" totalMs="+(System.nanoTime()-start)/1_000_000+" questions="+result.questions().size());
            String mixed="""
                    1. 单选题（5分）：哪个实践提高质量？ A.评审 B.忽略测试 C.隐藏缺陷 D.不写需求。答案：A。
                    2. 多选题（5分）：哪些实践提高质量？ A.评审 B.测试 C.隐藏缺陷 D.不写需求。答案：A、B。
                    3. 判断题（5分）：测试能证明不存在任何缺陷。答案：错误。
                    4. 简答题（5分）：解释单元测试。参考答案：验证单个单元的行为。
                    5. 分析题（5分）：分析紧耦合的影响。参考答案：增加变更传播风险。
                    6. 软件设计题（5分）：设计通知接口。参考答案：定义发送接口并封装具体实现。
                    7. 代码题（5分）：Java实现整数加法。参考代码：int sum(int a,int b){return a+b;}
                    8. 文档报告题：提交PDF测试报告，说明测试范围、用例及结果。未提供答案或分值。
                    """;
            var mixedSections=List.of(new ParsedSection(mixed,1,"Synthetic mixed assignment"));
            var batch=gateway.completeJsonStreaming(new AiRequest(ModelCapability.REASONING,1L,1L,template.identifier(),template.text(),"[PAGE 1]\n"+mixed),Duration.ofSeconds(300),QuestionExtractionService.Extracted.class,v->QuestionExtractionService.validate(v,mixedSections),n->QuestionDraftNormalizer.normalize(n,mixedSections));
            assertThat(batch.questions()).hasSize(8);
            assertThat(batch.questions().stream().map(QuestionExtractionService.ExtractedQuestion::type)).containsExactly("SINGLE_CHOICE","MULTIPLE_CHOICE","TRUE_FALSE","SHORT_ANSWER","ANALYSIS","DESIGN","CODE","DOCUMENT_REPORT");
            assertThat(batch.questions().get(1).correctChoiceIndexes()).containsExactly(0,1);
            assertThat(batch.questions().get(2).booleanAnswer()).isEqualTo("false");
            assertThat(batch.questions().get(7).score()).isEmpty();
            System.out.println("REAL_MIXED_IMPORT PASS questions=8 types=8");
        } finally {gateway.close();}
    }
}
