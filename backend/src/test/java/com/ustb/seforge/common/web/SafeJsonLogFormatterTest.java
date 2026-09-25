package com.ustb.seforge.common.web;
import static org.assertj.core.api.Assertions.*;
import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
class SafeJsonLogFormatterTest {
 @Test void excludesMessagesArgumentsThrowableAndArbitraryMdc() throws Exception {
  var logger=new LoggerContext().getLogger("provider");
  var event=new LoggingEvent("test",logger,Level.ERROR,"password=secret-sentinel prompt=private-sentinel",new IllegalStateException("session-sentinel"),new Object[]{"key-sentinel"});
  event.setMDCPropertyMap(Map.of("traceId","trace-123456","requestId","request-123456","Authorization","bearer-sentinel"));
  String output=new SafeJsonLogFormatter().format(event);
  assertThat(output).doesNotContain("sentinel");
  var json=new ObjectMapper().readTree(output);
  assertThat(json.path("traceId").asText()).isEqualTo("trace-123456");
  assertThat(json.path("errorType").asText()).isEqualTo("java.lang.IllegalStateException");
 }
}
