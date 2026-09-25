package com.ustb.seforge.content.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic engineering fixture, not a semantic-quality model. */
public final class PhaseThreeProviderStub implements AutoCloseable {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
    public final AtomicInteger embeddingCalls = new AtomicInteger();
    public final AtomicInteger chatCalls = new AtomicInteger();
    public volatile boolean failEmbedding;
    private static final List<String> TERMS = List.of("cohesion", "coupling", "traceability", "verification", "validation", "encapsulation", "regression", "equivalence", "boundary", "refactoring", "atomicity", "consistency", "isolation", "durability", "primarykey", "foreignkey", "normalization", "indexing", "deadlock", "optimistic");

    public PhaseThreeProviderStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
            server.setExecutor(executor);
            server.createContext("/v1/embeddings", exchange -> {
                embeddingCalls.incrementAndGet();
                if (failEmbedding) { exchange.sendResponseHeaders(503, -1); exchange.close(); return; }
                var request = json.readTree(exchange.getRequestBody());
                List<Map<String,Object>> data = new ArrayList<>();
                var input = request.get("input");
                var inputs = input.isArray() ? input : json.createArrayNode().add(input);
                int index = 0;
                for (var value : inputs) {
                    float[] vector = new float[32];
                    String text = value.asText().toLowerCase(Locale.ROOT);
                    for (int i = 0; i < TERMS.size(); i++) if (text.contains(TERMS.get(i))) vector[i] = 1;
                    boolean empty = true;
                    for (float component : vector) if (component != 0) empty = false;
                    if (empty) vector[31] = 1;
                    data.add(Map.of("object","embedding","index",index++,"embedding",vector));
                }
                byte[] body = json.writeValueAsBytes(Map.of("object","list","data",data,"model","phase3-v1",
                        "usage",Map.of("prompt_tokens",inputs.size(),"total_tokens",inputs.size())));
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body); exchange.close();
            });
            server.createContext("/v1/chat/completions", exchange -> {
                chatCalls.incrementAndGet();
                var messages = json.readTree(exchange.getRequestBody()).path("messages");
                // Fault controls apply only to the current request, never prior conversation history.
                String request = messages.path(messages.size() - 1).path("content").asText();
                exchange.getResponseHeaders().set("Content-Type","text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                try (var out = exchange.getResponseBody()) {
                    int count = request.contains("STREAM_SLOW") ? 100 : 1;
                    for (int i = 0; i < count; i++) {
                        var choice = Map.of("index",0,"delta",Map.of("content","Course evidence supports this explanation [C1]. "));
                        out.write(("data: " + json.writeValueAsString(Map.of("id","stub","object","chat.completion.chunk",
                                "choices",List.of(choice))) + "\n\n").getBytes(StandardCharsets.UTF_8));
                        out.flush();
                        if (request.contains("STREAM_FAIL")) {
                            out.write("data: invalid-json\n\n".getBytes(StandardCharsets.UTF_8)); out.flush(); return;
                        }
                        if (count > 1) try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                    }
                    out.write(("data: {\"id\":\"stub\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));
                } catch (java.io.IOException cancelled) { /* Client/provider cancellation closes the transport. */ }
                finally { exchange.close(); }
            });
            server.start();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    public String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1"; }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
