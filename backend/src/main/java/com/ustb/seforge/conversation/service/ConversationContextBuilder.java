package com.ustb.seforge.conversation.service;

import com.ustb.seforge.conversation.domain.ConversationMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Builds a bounded, deterministic conversation context without depending on a model provider.
 * Older messages are folded into a rolling extractive summary and the exact messages always form
 * one contiguous suffix of the conversation.
 */
final class ConversationContextBuilder {
    static final int DEFAULT_TOKEN_BUDGET = 4_000;
    static final int DEFAULT_SUMMARY_TOKEN_BUDGET = 1_000;
    static final int DEFAULT_SUMMARY_ENTRY_TOKEN_BUDGET = 120;

    private static final String SUMMARY_HEADER = "Summary of earlier messages:\n";
    private static final String OMITTED_MARKER = "[Earlier summary entries omitted]";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final int tokenBudget;
    private final int summaryTokenBudget;
    private final int summaryEntryTokenBudget;

    ConversationContextBuilder() {
        this(DEFAULT_TOKEN_BUDGET, DEFAULT_SUMMARY_TOKEN_BUDGET,
                DEFAULT_SUMMARY_ENTRY_TOKEN_BUDGET);
    }

    ConversationContextBuilder(int tokenBudget, int summaryTokenBudget, int summaryEntryTokenBudget) {
        if (tokenBudget < 32 || summaryTokenBudget < 8 || summaryTokenBudget >= tokenBudget
                || summaryEntryTokenBudget < 4 || summaryEntryTokenBudget >= summaryTokenBudget) {
            throw new IllegalArgumentException("Invalid conversation context token budgets");
        }
        this.tokenBudget = tokenBudget;
        this.summaryTokenBudget = summaryTokenBudget;
        this.summaryEntryTokenBudget = summaryEntryTokenBudget;
    }

    ContextWindow build(String priorSummary, Long summarizedThroughMessageId,
                        List<ConversationMessage> unsummarizedMessages) {
        List<ConversationMessage> messages = List.copyOf(unsummarizedMessages);
        validateOrder(messages, summarizedThroughMessageId);

        String unchanged = render(priorSummary, messages);
        if (estimateTokens(unchanged) <= tokenBudget) {
            return window(unchanged, priorSummary, summarizedThroughMessageId, messages);
        }

        int exactTokenBudget = tokenBudget - summaryTokenBudget;
        int exactTokens = 0;
        int exactStart = messages.size();
        for (int index = messages.size() - 1; index >= 0; index--) {
            int messageTokens = estimateTokens(renderMessage(messages.get(index)));
            if (exactTokens + messageTokens > exactTokenBudget) {
                break;
            }
            exactTokens += messageTokens;
            exactStart = index;
        }

        while (true) {
            List<ConversationMessage> summarized = messages.subList(0, exactStart);
            List<ConversationMessage> exact = messages.subList(exactStart, messages.size());
            String summary = rollSummary(priorSummary, summarized);
            Long throughMessageId = summarized.isEmpty()
                    ? summarizedThroughMessageId
                    : summarized.getLast().getId();
            String history = render(summary, exact);
            if (estimateTokens(history) <= tokenBudget || exact.isEmpty()) {
                return window(history, summary, throughMessageId, exact);
            }

            // Rounding in the conservative token estimator can consume a few extra tokens. Move
            // the oldest exact message into the summary; never skip a message in the middle.
            exactStart++;
        }
    }

    int estimateTokens(String value) {
        if (value == null || value.isEmpty()) return 0;
        // UTF-8 bytes / 3 is deliberately conservative for CJK while remaining deterministic and
        // independent of a provider-specific tokenizer.
        return Math.max(1, (value.getBytes(StandardCharsets.UTF_8).length + 2) / 3);
    }

    private ContextWindow window(String history, String summary, Long throughMessageId,
                                 List<ConversationMessage> exactMessages) {
        List<Long> exactIds = exactMessages.stream().map(ConversationMessage::getId).toList();
        return new ContextWindow(history, blankToNull(summary), throughMessageId, exactIds,
                estimateTokens(history));
    }

    private String rollSummary(String priorSummary, List<ConversationMessage> newlySummarized) {
        List<String> entries = new ArrayList<>();
        boolean earlierEntriesWereOmitted = false;
        if (priorSummary != null && !priorSummary.isBlank()) {
            for (String line : priorSummary.split("\\R")) {
                String normalized = normalize(line);
                if (normalized.isEmpty()) continue;
                if (OMITTED_MARKER.equals(normalized)) {
                    earlierEntriesWereOmitted = true;
                } else {
                    entries.add(abbreviate(normalized, summaryEntryTokenBudget));
                }
            }
        }
        for (ConversationMessage message : newlySummarized) {
            String prefix = "[#" + message.getId() + "] " + message.getRole() + ": ";
            int contentBudget = Math.max(4,
                    summaryEntryTokenBudget - estimateTokens(prefix));
            entries.add(prefix + abbreviate(normalize(message.getContent()), contentBudget));
        }
        if (entries.isEmpty()) return earlierEntriesWereOmitted ? OMITTED_MARKER : null;

        LinkedList<String> retained = new LinkedList<>();
        int used = estimateTokens(SUMMARY_HEADER);
        int index = entries.size() - 1;
        for (; index >= 0; index--) {
            String entry = entries.get(index);
            int entryTokens = estimateTokens(entry + '\n');
            if (used + entryTokens > summaryTokenBudget) break;
            retained.addFirst(entry);
            used += entryTokens;
        }

        boolean omitted = earlierEntriesWereOmitted || index >= 0;
        if (retained.isEmpty()) {
            int available = Math.max(1, summaryTokenBudget - used);
            String retainedEntry = abbreviate(entries.getLast(), available);
            retained.add(retainedEntry);
            used += estimateTokens(retainedEntry + '\n');
        }
        if (omitted) {
            int markerTokens = estimateTokens(OMITTED_MARKER + '\n');
            while (retained.size() > 1 && used + markerTokens > summaryTokenBudget) {
                used -= estimateTokens(retained.removeFirst() + '\n');
            }
            if (used + markerTokens <= summaryTokenBudget) retained.addFirst(OMITTED_MARKER);
        }
        return String.join("\n", retained);
    }

    private String render(String summary, List<ConversationMessage> exactMessages) {
        StringBuilder value = new StringBuilder();
        if (summary != null && !summary.isBlank()) {
            value.append(SUMMARY_HEADER).append(summary.trim()).append('\n');
        }
        exactMessages.forEach(message -> value.append(renderMessage(message)));
        return value.toString();
    }

    private String renderMessage(ConversationMessage message) {
        return message.getRole() + ": " + message.getContent() + "\n";
    }

    private String abbreviate(String value, int maxTokens) {
        if (estimateTokens(value) <= maxTokens) return value;
        String suffix = "…";
        int codePoints = value.codePointCount(0, value.length());
        int low = 0;
        int high = codePoints;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            int end = value.offsetByCodePoints(0, middle);
            if (estimateTokens(value.substring(0, end) + suffix) <= maxTokens) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return value.substring(0, value.offsetByCodePoints(0, low)).stripTrailing() + suffix;
    }

    private String normalize(String value) {
        return value == null ? "" : WHITESPACE.matcher(value).replaceAll(" ").trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void validateOrder(List<ConversationMessage> messages, Long summarizedThroughMessageId) {
        Long previous = summarizedThroughMessageId;
        for (ConversationMessage message : messages) {
            if (message.getId() == null) {
                throw new IllegalArgumentException("Conversation context only accepts persisted messages");
            }
            if (previous != null && message.getId() <= previous) {
                throw new IllegalArgumentException("Conversation messages must be strictly ordered");
            }
            previous = message.getId();
        }
    }

    record ContextWindow(String history, String summary, Long summarizedThroughMessageId,
                         List<Long> exactMessageIds, int estimatedTokens) {
        ContextWindow {
            exactMessageIds = Collections.unmodifiableList(new ArrayList<>(exactMessageIds));
        }
    }
}
