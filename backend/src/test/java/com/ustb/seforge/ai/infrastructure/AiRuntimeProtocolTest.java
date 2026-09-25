package com.ustb.seforge.ai.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.ustb.seforge.ai.application.*;
import com.ustb.seforge.ai.domain.AiTrace;
import com.ustb.seforge.config.SEForgeProperties;
import dev.langchain4j.data.segment.TextSegment;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiRuntimeProtocolTest {
    private static final String ANSWER = "{\"id\":\"stub\",\"object\":\"chat.completion\",\"model\":\"stub\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"answer\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2,\"total_tokens\":5}}";

    @Test void routesAllCapabilitiesAndRetriesPrimaryBeforeFallbackUsingHttpProtocol() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger primary = new AtomicInteger(), fallback = new AtomicInteger(), embeddings = new AtomicInteger();
        server.createContext("/primary/chat/completions", exchange -> {
            primary.incrementAndGet();
            byte[] body = "{\"error\":{\"message\":\"rate limited\",\"type\":\"rate_limit\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/fallback/chat/completions", exchange -> {
            fallback.incrementAndGet(); byte[] body = ANSWER.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/fallback/embeddings", exchange -> {
            embeddings.incrementAndGet();
            byte[] body = "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[0.2,0.4]}],\"model\":\"test-embedding\",\"usage\":{\"prompt_tokens\":2,\"total_tokens\":2}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var properties = settings(server.getAddress().getPort());
            var registry = new ModelRegistry(properties);
            var gateway = gateway(registry);
            for (var capability : List.of(ModelCapability.FAST, ModelCapability.REASONING, ModelCapability.CODING)) {
                assertThat(gateway.complete(new AiRequest(capability, 7L, 9L, "test:v1", "system", "question")).provider())
                        .isEqualTo("dashscope");
            }
            assertThat(primary).hasValue(6); assertThat(fallback).hasValue(3);
            assertThat(registry.available(ModelCapability.EMBEDDING)).isTrue();
            assertThat(gateway.embed("test-embedding", List.of(TextSegment.from("hello"))).getFirst().dimension()).isEqualTo(2);
            assertThat(embeddings).hasValue(1);
        } finally { server.stop(0); }
    }

    @Test void unavailableProviderTimesOutAndMissingKeysStayDegraded() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/primary/chat/completions", exchange -> {
            try { Thread.sleep(350); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            var properties = settings(server.getAddress().getPort());
            properties.getAi().setDashscopeApiKey(""); properties.getAi().setMaxRetries(0);
            properties.getAi().setTimeout(Duration.ofMillis(50));
            var gateway = gateway(new ModelRegistry(properties));
            assertThatThrownBy(() -> gateway.complete(new AiRequest(ModelCapability.FAST, 7L, 9L, "test:v1", "", "q")))
                    .isInstanceOf(AiUnavailableException.class).hasMessage("AI provider unavailable");
            properties.getAi().setDeepseekApiKey("");
            var empty = new ModelRegistry(properties);
            for (var capability : ModelCapability.values()) assertThat(empty.available(capability)).isFalse();
            assertThatThrownBy(() -> gateway(empty).embed("test", List.of(TextSegment.from("x"))))
                    .isInstanceOf(AiUnavailableException.class);
        } finally { server.stop(0); }
    }

    private SEForgeProperties settings(int port) {
        var properties = new SEForgeProperties(); var ai = properties.getAi();
        ai.setEnabled(true); ai.setDeepseekApiKey("stub-only"); ai.setDashscopeApiKey("stub-only");
        ai.setDeepseekBaseUrl("http://127.0.0.1:" + port + "/primary");
        ai.setDashscopeBaseUrl("http://127.0.0.1:" + port + "/fallback");
        ai.setEmbeddingDimension(2); ai.setMaxRetries(1); ai.setTimeout(Duration.ofSeconds(2));
        return properties;
    }
    private AiGateway gateway(ModelRegistry registry) {
        var traces = mock(AiTraceService.class); var trace = mock(AiTrace.class);
        when(trace.getId()).thenReturn(1L);
        when(traces.begin(any(), anyString(), anyString())).thenReturn(trace);
        return new AiGateway(new ModelRouter(registry), traces, new ObjectMapper(), new AiServiceFactory(registry));
    }
}
