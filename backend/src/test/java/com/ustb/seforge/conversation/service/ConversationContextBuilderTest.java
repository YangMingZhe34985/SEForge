package com.ustb.seforge.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ustb.seforge.conversation.domain.ConversationMessage;
import com.ustb.seforge.conversation.domain.MessageRole;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ConversationContextBuilderTest {

    @Test
    void keepsTheCompleteOrderedHistoryWhenItFits() {
        ConversationContextBuilder builder = new ConversationContextBuilder(160, 48, 16);
        List<ConversationMessage> messages = List.of(
                message(1, MessageRole.USER, "What is cohesion?"),
                message(2, MessageRole.ASSISTANT, "It measures how focused a module is."));

        ConversationContextBuilder.ContextWindow result = builder.build(null, null, messages);

        assertThat(result.history()).isEqualTo("USER: What is cohesion?\n"
                + "ASSISTANT: It measures how focused a module is.\n");
        assertThat(result.summary()).isNull();
        assertThat(result.summarizedThroughMessageId()).isNull();
        assertThat(result.exactMessageIds()).containsExactly(1L, 2L);
        assertThat(result.estimatedTokens()).isLessThanOrEqualTo(160);
    }

    @Test
    void summarizesOnlyAPrefixAndNeverBuildsAHistoryWithHoles() {
        ConversationContextBuilder builder = new ConversationContextBuilder(120, 42, 14);
        List<ConversationMessage> messages = messages(1, 8, 72);

        ConversationContextBuilder.ContextWindow result = builder.build(null, null, messages);

        assertThat(result.summary()).isNotBlank();
        assertThat(result.summarizedThroughMessageId()).isPositive();
        assertThat(result.exactMessageIds()).isNotEmpty();
        long firstExact = result.exactMessageIds().getFirst();
        assertThat(result.summarizedThroughMessageId()).isEqualTo(firstExact - 1);
        assertThat(result.exactMessageIds()).containsExactlyElementsOf(
                java.util.stream.LongStream.rangeClosed(firstExact, 8).boxed().toList());
        assertThat(result.estimatedTokens()).isLessThanOrEqualTo(120);
    }

    @Test
    void advancesTheWatermarkAndRollsAnExistingSummaryWithoutAProvider() {
        ConversationContextBuilder builder = new ConversationContextBuilder(96, 36, 12);
        ConversationContextBuilder.ContextWindow first = builder.build(null, null,
                messages(1, 6, 70));
        long previousWatermark = first.summarizedThroughMessageId();
        List<ConversationMessage> pending = messages(previousWatermark + 1, 12, 70);

        ConversationContextBuilder.ContextWindow second = builder.build(first.summary(),
                previousWatermark, pending);

        assertThat(second.summarizedThroughMessageId()).isGreaterThan(previousWatermark);
        assertThat(second.summary()).contains("[#");
        assertThat(second.exactMessageIds()).allSatisfy(
                id -> assertThat(id).isGreaterThan(second.summarizedThroughMessageId()));
        assertThat(second.estimatedTokens()).isLessThanOrEqualTo(96);
    }

    @Test
    void tokenBudgetIsConservativeForChineseContent() {
        ConversationContextBuilder builder = new ConversationContextBuilder(80, 28, 10);
        List<ConversationMessage> messages = List.of(
                message(1, MessageRole.USER, "请解释高内聚低耦合的设计原则。".repeat(8)),
                message(2, MessageRole.ASSISTANT, "每个模块应该聚焦单一职责。".repeat(8)));

        ConversationContextBuilder.ContextWindow result = builder.build(null, null, messages);

        assertThat(result.estimatedTokens()).isLessThanOrEqualTo(80);
        assertThat(builder.estimateTokens(result.history())).isEqualTo(result.estimatedTokens());
    }

    @Test
    void rejectsOutOfOrderInputInsteadOfSilentlyCreatingANonContiguousWindow() {
        ConversationContextBuilder builder = new ConversationContextBuilder(80, 28, 10);

        assertThatThrownBy(() -> builder.build(null, 2L, List.of(
                message(4, MessageRole.USER, "newer"),
                message(3, MessageRole.ASSISTANT, "older"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly ordered");
    }

    private List<ConversationMessage> messages(long firstId, long lastId, int contentLength) {
        List<ConversationMessage> values = new ArrayList<>();
        for (long id = firstId; id <= lastId; id++) {
            MessageRole role = id % 2 == 0 ? MessageRole.ASSISTANT : MessageRole.USER;
            values.add(message(id, role, ("message-" + id + " ").repeat(contentLength / 5)));
        }
        return values;
    }

    private ConversationMessage message(long id, MessageRole role, String content) {
        ConversationMessage value = new ConversationMessage(20L, 10L,
                role == MessageRole.USER ? 30L : null, role, content, null, null, null);
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }
}
