package com.ustb.seforge.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ustb.seforge.conversation.domain.Conversation;
import com.ustb.seforge.conversation.domain.ConversationMessage;
import com.ustb.seforge.conversation.domain.MessageRole;
import com.ustb.seforge.conversation.repository.AnswerFeedbackRepository;
import com.ustb.seforge.conversation.repository.ConversationMessageRepository;
import com.ustb.seforge.conversation.repository.ConversationRepository;
import com.ustb.seforge.conversation.repository.MessageCitationRepository;
import com.ustb.seforge.course.service.CourseAccessService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConversationContextServiceTest {
    @Mock ConversationRepository conversations;
    @Mock ConversationMessageRepository messages;
    @Mock MessageCitationRepository citations;
    @Mock AnswerFeedbackRepository feedback;
    @Mock CourseAccessService access;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        service = new ConversationService(conversations, messages, citations, feedback, access);
    }

    @Test
    void persistsSummaryStateAndQueriesOnlyMessagesAfterItsWatermark() {
        Conversation conversation = new Conversation(11L, 7L, "Conversation");
        ReflectionTestUtils.setField(conversation, "id", 21L);
        conversation.updateSummary("[#4] ASSISTANT: prior context", 4L);
        List<ConversationMessage> pending = messages(5, 16, 1_500);
        when(conversations.findByIdAndOwnerIdAndCourseId(21L, 7L, 11L))
                .thenReturn(Optional.of(conversation));
        when(messages.findByConversationIdAndClientRequestId(21L, "req-1"))
                .thenReturn(Optional.empty());
        when(messages.findByConversationIdAndIdGreaterThanOrderByIdAsc(21L, 4L))
                .thenReturn(pending);
        when(messages.save(any(ConversationMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConversationService.QuestionContext context = service.addQuestion(
                11L, 21L, 7L, "req-1", "What should I improve?");

        verify(messages).findByConversationIdAndIdGreaterThanOrderByIdAsc(21L, 4L);
        assertThat(conversation.getSummaryThroughMessageId()).isGreaterThan(4L);
        assertThat(conversation.getSummary()).isNotBlank();
        assertThat(new ConversationContextBuilder().estimateTokens(context.history()))
                .isLessThanOrEqualTo(ConversationContextBuilder.DEFAULT_TOKEN_BUDGET);
    }

    private List<ConversationMessage> messages(long firstId, long lastId, int contentLength) {
        List<ConversationMessage> values = new ArrayList<>();
        for (long id = firstId; id <= lastId; id++) {
            MessageRole role = id % 2 == 0 ? MessageRole.ASSISTANT : MessageRole.USER;
            ConversationMessage message = new ConversationMessage(21L, 11L,
                    role == MessageRole.USER ? 7L : null, role,
                    ("context-" + id + " ").repeat(contentLength / 8), null, null, null);
            ReflectionTestUtils.setField(message, "id", id);
            values.add(message);
        }
        return values;
    }
}
