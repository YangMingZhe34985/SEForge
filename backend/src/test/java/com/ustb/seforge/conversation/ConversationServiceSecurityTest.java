package com.ustb.seforge.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.conversation.api.ConversationView;
import com.ustb.seforge.conversation.domain.Conversation;
import com.ustb.seforge.conversation.domain.ConversationStatus;
import com.ustb.seforge.conversation.repository.AnswerFeedbackRepository;
import com.ustb.seforge.conversation.repository.ConversationMessageRepository;
import com.ustb.seforge.conversation.repository.ConversationRepository;
import com.ustb.seforge.conversation.repository.MessageCitationRepository;
import com.ustb.seforge.conversation.service.ConversationService;
import com.ustb.seforge.course.service.CourseAccessService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ConversationServiceSecurityTest {
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
    void foreignConversationIsHiddenAsNotFoundBeforeMessagesAreRead() {
        when(conversations.findByIdAndOwnerIdAndCourseId(99L, 7L, 11L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.messages(11L, 99L, 7L, 0, 50))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        verify(messages, never()).findAllByConversationIdOrderByIdAsc(any(), any());
    }

    @Test
    void listIsCourseAndOwnerScopedAndReturnsBoundedPageContract() {
        Conversation value = new Conversation(11L, 7L, "Scoped");
        when(conversations.findAllByOwnerIdAndCourseIdAndStatusOrderByUpdatedAtDesc(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.eq(ConversationStatus.ACTIVE), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(3);
                    return new PageImpl<>(List.of(value), pageable, 1);
                });

        PageResponse<ConversationView> result = service.list(11L, 7L, -1, 1000);

        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).extracting(ConversationView::courseId).containsExactly(11L);
        verify(access).requireMember(11L, 7L);
    }
}
