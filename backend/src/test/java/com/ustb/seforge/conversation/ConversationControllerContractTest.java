package com.ustb.seforge.conversation;

import com.ustb.seforge.common.exception.GlobalExceptionHandler;
import com.ustb.seforge.conversation.api.ConversationController;
import com.ustb.seforge.conversation.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConversationControllerContractTest {
    @Test void malformedJsonIsNotMistakenForAnSseDisconnect() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ConversationController(mock(ConversationService.class),
                        mock(CourseQaStreamService.class), mock(ConversationGenerationService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/courses/1/conversations/1/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
