package com.ustb.seforge.assignment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.seforge.assignment.api.TutorRequest;
import com.ustb.seforge.assignment.api.TutorResponseView;
import com.ustb.seforge.assignment.repository.TutorReplayRepository;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** HTTP replay guard only; does not change Tutor policy, model routing or job execution. */
@Service
public class TutorRequestService {
    private final TutorService tutor;
    private final AssignmentTool assignments;
    private final TutorReplayRepository replay;
    private final ObjectMapper mapper;
    public TutorRequestService(TutorService tutor, AssignmentTool assignments, TutorReplayRepository replay, ObjectMapper mapper) {
        this.tutor=tutor; this.assignments=assignments; this.replay=replay; this.mapper=mapper;
    }
    public TutorResponseView ask(long assignment, long user, TutorRequest request) {
        // Reauthorize even a cached result. The model cannot choose the principal or assignment.
        assignments.load(assignment,request.questionId(),user);
        if(request.requestKey()==null) return tutor.ask(assignment,user,request); // legacy callers
        String key=request.requestKey();
        String hash;
        try {
            hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    (assignment+":"+mapper.writeValueAsString(request)).getBytes(StandardCharsets.UTF_8)));
        } catch(Exception e) { throw new IllegalStateException("Cannot fingerprint Tutor request",e); }
        if(!replay.claim(user,key,assignment,hash)) {
            var entry=replay.get(user,key);
            if(!entry.hash().equals(hash)) throw new AppException(ErrorCode.CONFLICT,"Tutor 请求标识已用于不同输入");
            if(entry.status().equals("COMPLETED")) {
                try { return mapper.readValue(entry.json(),TutorResponseView.class); }
                catch(Exception e) { throw new AppException(ErrorCode.INTERNAL_ERROR,"Tutor 已完成，但结果恢复失败；请凭追踪号联系管理员"); }
            }
            if(entry.status().equals("FAILED")) {
                Object details=null;
                try { if(entry.json()!=null) details=mapper.readTree(entry.json()); }
                catch(Exception ignored) { /* Still preserve the saved safe error code/message. */ }
                throw new AppException(ErrorCode.valueOf(entry.code()),entry.message(),details);
            }
            throw new AppException(ErrorCode.TUTOR_PROCESSING,"Tutor 请求已受理，结果尚未确认；请稍后恢复。若服务曾重启，请凭追踪号核对状态，不要重复生成",
                    Map.of("stage","PROCESSING","requestKey",key));
        }
        TutorResponseView result;
        try { result=tutor.ask(assignment,user,request); }
        catch(RuntimeException failure) {
            AppException diagnostic=TutorFailureDiagnostics.map(failure);
            String details=null;
            try { details=mapper.writeValueAsString(diagnostic.getDetails()); }
            catch(Exception ignored) { /* Diagnostic metadata is optional. */ }
            replay.fail(user,key,diagnostic.getErrorCode().name(),diagnostic.getMessage(),details);
            throw diagnostic;
        }
        // A persistence failure leaves PROCESSING rather than permitting a second model invocation.
        try { replay.complete(user,key,mapper.writeValueAsString(result)); }
        catch(Exception failure) { throw new AppException(ErrorCode.INFRASTRUCTURE_UNAVAILABLE,"Tutor 已生成，但结果保存失败；请保留追踪号，不要重复生成"); }
        return result;
    }
}
