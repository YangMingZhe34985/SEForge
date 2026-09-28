package com.ustb.seforge.assignment.service;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import java.util.*;

final class TutorFailureDiagnostics {
    private TutorFailureDiagnostics() {}
    static AppException tool(Throwable failure, com.ustb.seforge.ai.application.AiToolCall call) {
        AppException cause=map(failure);
        boolean known=cause.getErrorCode()!=ErrorCode.INTERNAL_ERROR && cause.getErrorCode()!=ErrorCode.TUTOR_PROVIDER_FAILED;
        var details=new LinkedHashMap<String,Object>();
        details.put("stage","TOOL"); details.put("tool",call.name());
        details.put("reason",known?cause.getErrorCode().name():Objects.toString(call.errorCode(),"TOOL_ERROR"));
        if(known && cause.getDetails()!=null) details.put("cause",cause.getDetails());
        return new AppException(known?cause.getErrorCode():ErrorCode.TUTOR_TOOL_FAILED,
                "Tutor 工具 "+call.name()+" 失败："+(known?cause.getMessage():"请凭追踪号检查课程检索与工具记录"),details);
    }
    static AppException map(Throwable failure) {
        var chain=new ArrayList<Throwable>();
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable c=failure;c!=null&&seen.add(c);c=c.getCause()) chain.add(c);
        // Preserve service errors (RAG/storage/authorization) wrapped by the model SDK.
        for(Throwable c:chain) if(c instanceof AppException app && !(c instanceof com.ustb.seforge.ai.application.AiUnavailableException)) return app;
        for(Throwable c:chain) {
            String name=c.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if(name.contains("timeout") || c instanceof java.util.concurrent.TimeoutException)
                return error(ErrorCode.TUTOR_TIMEOUT,"MODEL_OR_TOOL","TIMEOUT","Tutor 模型或工具调用超时；本次调用已失败，可稍后重试");
            if(name.contains("authentication")||name.contains("authorization"))
                return error(ErrorCode.TUTOR_PROVIDER_FAILED,"PROVIDER","AUTHENTICATION","Tutor 模型服务鉴权失败，请检查 REASONING 路由、密钥和模型权限");
            if(name.contains("ratelimit"))
                return error(ErrorCode.TUTOR_PROVIDER_FAILED,"PROVIDER","RATE_LIMIT","Tutor 模型服务限流或额度不足，请稍后重试或检查配额");
            if(name.contains("connect"))
                return error(ErrorCode.TUTOR_PROVIDER_FAILED,"PROVIDER","CONNECTION","Tutor 模型服务连接失败，请检查供应商地址与网络");
        }
        if(failure instanceof com.ustb.seforge.ai.application.AiUnavailableException)
            return error(ErrorCode.TUTOR_PROVIDER_FAILED,"PROVIDER","UNAVAILABLE","Tutor 模型或工具执行未完成，请凭追踪号检查 AI Trace 的状态和 Tool 调用记录");
        return error(ErrorCode.INTERNAL_ERROR,"TUTOR","INTERNAL","Tutor 处理失败，请凭追踪号检查服务端日志");
    }
    private static AppException error(ErrorCode code,String stage,String reason,String message) {
        return new AppException(code,message,Map.of("stage",stage,"reason",reason));
    }
}
