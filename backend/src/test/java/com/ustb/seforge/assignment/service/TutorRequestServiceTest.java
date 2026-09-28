package com.ustb.seforge.assignment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.assignment.api.*;
import com.ustb.seforge.assignment.domain.TutorOperation;
import com.ustb.seforge.assignment.repository.TutorReplayRepository;
import com.ustb.seforge.common.exception.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class TutorRequestServiceTest {
    @Test void retriesReplayAndReauthorizeWithoutCallingModelAgain() throws Exception {
        var tutor=mock(TutorService.class); var access=mock(AssignmentTool.class);
        var repository=mock(TutorReplayRepository.class); var mapper=new ObjectMapper();
        var service=new TutorRequestService(tutor,access,repository,mapper);
        var request=new TutorRequest(3L,TutorOperation.HINT,null,"00000000-0000-4000-8000-000000000001");
        var result=new TutorResponseView(4L,TutorOperation.HINT,"中文提示",true,"提示",List.of());
        when(tutor.ask(1L,2L,request)).thenReturn(result);
        var hash=new java.util.concurrent.atomic.AtomicReference<String>();
        when(repository.claim(eq(2L),eq(request.requestKey()),eq(1L),anyString())).thenAnswer(i->{hash.set(i.getArgument(3));return true;});
        assertThat(service.ask(1,2,request)).isEqualTo(result);
        when(repository.claim(eq(2L),eq(request.requestKey()),eq(1L),anyString())).thenReturn(false);
        when(repository.get(2,request.requestKey())).thenReturn(new TutorReplayRepository.Entry(hash.get(),"COMPLETED",mapper.writeValueAsString(result),null,null));
        assertThat(service.ask(1,2,request)).isEqualTo(result);
        verify(tutor,times(1)).ask(1L,2L,request);
        verify(access,times(2)).load(1L,3L,2L);
        when(repository.get(2,request.requestKey())).thenReturn(new TutorReplayRepository.Entry(hash.get(),"PROCESSING",null,null,null));
        assertThatThrownBy(()->service.ask(1,2,request)).isInstanceOfSatisfying(AppException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TUTOR_PROCESSING));
        verifyNoMoreInteractions(tutor);
        doThrow(new AppException(ErrorCode.ACCESS_DENIED,"Denied")).when(access).load(1L,3L,2L);
        assertThatThrownBy(()->service.ask(1,2,request)).isInstanceOf(AppException.class).hasMessage("Denied");
    }
    @Test void safeDiagnosticsRetainNestedRagErrorsAndDoNotEchoSecrets() {
        var rag=new AppException(ErrorCode.VECTOR_STORE_UNAVAILABLE,"课程检索不可用");
        assertThat(TutorFailureDiagnostics.map(new RuntimeException(rag))).isSameAs(rag);
        var toolError=TutorFailureDiagnostics.tool(new com.ustb.seforge.ai.application.AiUnavailableException("wrapped",rag),
                com.ustb.seforge.ai.application.AiToolCall.failed("search_course_knowledge",12,rag));
        assertThat(toolError.getErrorCode()).isEqualTo(ErrorCode.VECTOR_STORE_UNAVAILABLE);
        assertThat(toolError.getMessage()).contains("search_course_knowledge","课程检索不可用");
        var unknown=new IllegalStateException("secret tool message");
        assertThat(TutorFailureDiagnostics.tool(unknown,com.ustb.seforge.ai.application.AiToolCall.failed("search_course_knowledge",1,unknown)).getErrorCode()).isEqualTo(ErrorCode.TUTOR_TOOL_FAILED);
        var timeout=TutorFailureDiagnostics.map(new com.ustb.seforge.ai.application.AiUnavailableException("unavailable",new java.net.SocketTimeoutException("secret-provider-body")));
        assertThat(timeout.getErrorCode()).isEqualTo(ErrorCode.TUTOR_TIMEOUT);
        assertThat(timeout.getMessage()).doesNotContain("secret-provider-body");
    }
}
