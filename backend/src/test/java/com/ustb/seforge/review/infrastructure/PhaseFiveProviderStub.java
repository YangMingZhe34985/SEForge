package com.ustb.seforge.review.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Controlled HTTP provider; production Gateway, model selection and traces remain in use. */
final class PhaseFiveProviderStub implements AutoCloseable {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
    volatile String override;
    volatile long delayMillis;
    final AtomicInteger calls = new AtomicInteger();

    PhaseFiveProviderStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
            server.setExecutor(executor);
            server.createContext("/v1/chat/completions", exchange -> {
                calls.incrementAndGet();
                var messages = json.readTree(exchange.getRequestBody()).path("messages");
                String content = messages.path(messages.size() - 1).path("content").asText();
                String answer = override;
                if (answer == null) {
                    Object result;
                    if (content.contains("Static-analysis source:")) {
                        var findings = json.readTree(content.substring(content.indexOf("Findings:\n") + 10));
                        List<Object> explanations = new ArrayList<>();
                        findings.forEach(f -> explanations.add(Map.of("findingKey", f.path("findingKey").asText(),
                                "explanation", "Explanation of the supplied finding", "impact", "Maintainability impact", "remediation", "Follow the supplied rule")));
                        result = Map.of("summary", "Static findings explained", "explanations", explanations);
                    } else if (content.startsWith("{")) {
                        var material = json.readTree(content);
                        List<Object> items = new ArrayList<>();
                        material.path("rubric").path("items").forEach(i -> items.add(Map.of("rubricItemId", i.path("id").asLong(),
                                "suggestedScore", 4, "evidence", List.of("Submission answer evidence"), "issues", List.of(), "feedback", "Improve the explanation")));
                        result = Map.of("summary", "Advisory assessment", "totalSuggestedScore", items.size() * 4, "rubricItems", items);
                    } else {
                        result = Map.of("summary", "Document assessment", "dimensions", List.of("completeness", "consistency", "verifiability", "clarity").stream()
                                .map(name -> Map.of("dimension", name, "score", 80, "findings", List.of("Section evidence"), "suggestions", List.of("Clarify section"))).toList(),
                                "issues", List.of(), "recommendations", List.of("Add acceptance criteria"));
                    }
                    answer = json.writeValueAsString(result);
                }
                long delay = delayMillis;
                if (delay > 0) try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                byte[] body = json.writeValueAsBytes(Map.of("id", "phase5-stub", "object", "chat.completion", "model", "phase5-controlled",
                        "choices", List.of(Map.of("index", 0, "message", Map.of("role", "assistant", "content", answer), "finish_reason", "stop")),
                        "usage", Map.of("prompt_tokens", 20, "completion_tokens", 20, "total_tokens", 40)));
                try {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
                } catch (java.io.IOException disconnected) { /* Killed worker closes its request. */ }
                finally { exchange.close(); }
            });
            server.start();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1"; }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
