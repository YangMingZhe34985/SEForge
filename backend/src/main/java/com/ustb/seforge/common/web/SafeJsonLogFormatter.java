package com.ustb.seforge.common.web;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

/** Production logs deliberately omit arguments, exception messages and arbitrary MDC.
 * Vendor/framework messages can contain SQL, credentials or HTTP bodies, so only their category
 * is recorded. Detailed AI/job outcome metadata belongs to the authorized database audit APIs.
 */
public class SafeJsonLogFormatter implements StructuredLogFormatter<ILoggingEvent> {
    private final ObjectMapper json = new ObjectMapper();
    @Override public String format(ILoggingEvent event) {
        Map<String,Object> value=new LinkedHashMap<>();
        value.put("timestamp",event.getInstant().toString()); value.put("level",event.getLevel().toString());
        value.put("logger",event.getLoggerName());
        // No free-form message survives: this also covers future accidentally concatenated inputs.
        value.put("eventCode",Integer.toUnsignedString(event.getMessage()==null?0:event.getMessage().hashCode(),16));
        value.put("message", "Application event; use traceId and authorized audit records for context");
        for(String key: new String[]{"traceId","requestId"}) {
            String id=event.getMDCPropertyMap().get(key);
            if(id!=null && id.matches("[a-zA-Z0-9_-]{8,64}")) value.put(key,id);
        }
        if(event.getThrowableProxy()!=null) value.put("errorType",event.getThrowableProxy().getClassName());
        if(event.getArgumentArray()!=null) for(Object argument:event.getArgumentArray()) {
            if(argument instanceof String type && type.matches("[A-Za-z]{1,128}(Exception|Error)")) value.put("errorType",type);
        }
        try{return json.writeValueAsString(value)+"\n";}catch(Exception ignored){return "{\"level\":\"ERROR\",\"message\":\"Log encoding failed\"}\n";}
    }
}
