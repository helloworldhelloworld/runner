package com.lightweightai.kernel.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMResponse;
import java.util.*;

/** Thread-safe admission and attempt ledger shared by one root execution and its descendants. */
public final class ExecutionUsageScope {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final long maxBudgetTokens;
    private final CostTracker legacyTracker;
    private final List<Attempt> attempts = new ArrayList<>();
    private long knownInput, knownOutput, unknownAttempts, chargedTokens, inFlightTokens;

    public ExecutionUsageScope(int maxBudgetTokens) { this(maxBudgetTokens, null); }
    public ExecutionUsageScope(int maxBudgetTokens, CostTracker legacyTracker) {
        this.maxBudgetTokens = Math.max(0, maxBudgetTokens);
        this.legacyTracker = legacyTracker;
        if (legacyTracker != null) {
            knownInput = legacyTracker.getConsumedInputTokens();
            knownOutput = legacyTracker.getConsumedOutputTokens();
            chargedTokens = knownInput + knownOutput;
        }
    }

    /** Reserve atomically before touching the remote peer. Estimates are never reported as actual usage. */
    public synchronized Attempt reserve(String provider, String model, List<ConversationMessage> messages,
                                        LLMOptions options) {
        long input = estimateInput(messages, options);
        int output = options != null && options.getMaxTokens() != null ? options.getMaxTokens() : 4096;
        if (output <= 0) throw new IllegalArgumentException("maxTokens must be positive");
        if (maxBudgetTokens > 0) {
            long availableOutput = maxBudgetTokens - chargedTokens - input;
            if (availableOutput <= 0) throw new BudgetStoppedException(snapshot());
            output = (int) Math.min(output, availableOutput);
        }
        var attempt = new Attempt(provider, model, input, output);
        chargedTokens += attempt.reservedTokens;
        inFlightTokens += attempt.reservedTokens;
        attempts.add(attempt);
        return attempt;
    }

    /** Complete content and schemas, including image payloads; framing allowance is a heuristic, not a tokenizer. */
    private static long estimateInput(List<ConversationMessage> messages, LLMOptions options) {
        List<Object> payload = new ArrayList<>();
        for (var message : messages) payload.add(Map.of("role", message.getRole().name(),
                "content", message.getContent(), "metadata", message.getMetadata()));
        Map<String,Object> config = new LinkedHashMap<>();
        config.put("messages", payload);
        if (options != null) {
            config.put("tools", options.getToolDefinitions());
            config.put("legacyTools", options.getTools().stream().map(tool -> tool.toJsonSchema()).toList());
            config.put("system", options.getSystemPrompt());
        }
        try { return 32L + JSON.writeValueAsBytes(config).length + 16L * messages.size(); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Cannot estimate model input", e); }
    }

    public synchronized Map<String,Object> snapshot() {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("maxBudgetTokens", maxBudgetTokens);
        data.put("knownInputTokens", knownInput); data.put("knownOutputTokens", knownOutput);
        data.put("knownTokens", knownInput + knownOutput); data.put("unknownAttempts", unknownAttempts);
        data.put("chargedTokens", chargedTokens); data.put("inFlightTokens", inFlightTokens);
        data.put("remainingTokens", maxBudgetTokens > 0 ? Math.max(0, maxBudgetTokens - chargedTokens) : null);
        data.put("attemptCount", attempts.size()); data.put("estimator", "utf8_bytes_plus_framing");
        data.put("exactBillingLimit", false);
        return Collections.unmodifiableMap(data);
    }

    public synchronized List<Map<String,Object>> attempts() {
        return attempts.stream().map(Attempt::snapshot).toList();
    }

    public enum Status { IN_FLIGHT, SUCCEEDED, FAILED, CANCELLED }

    /** Single-settlement reservation; a late completion cannot refund a cancelled attempt. */
    public final class Attempt {
        private final String id = UUID.randomUUID().toString();
        private final String provider, model;
        private final long estimatedInputTokens, reservedTokens;
        private final int maxTokens;
        private Status status = Status.IN_FLIGHT;
        private Integer inputTokens, outputTokens;
        private Attempt(String provider, String model, long input, int output) {
            this.provider = provider; this.model = model; estimatedInputTokens = input; maxTokens = output;
            reservedTokens = input + output;
        }
        public int maxTokens() { return maxTokens; }
        public void finish(LLMResponse response, Status terminal) {
            if (terminal == Status.IN_FLIGHT) throw new IllegalArgumentException("Terminal status required");
            synchronized (ExecutionUsageScope.this) {
                if (status != Status.IN_FLIGHT) return;
                status = terminal;
                inFlightTokens -= reservedTokens;
                var usage = response == null ? null : response.getUsage();
                if (usage != null && usage.getInputTokens() >= 0 && usage.getOutputTokens() >= 0) {
                    inputTokens = usage.getInputTokens(); outputTokens = usage.getOutputTokens();
                    knownInput += inputTokens; knownOutput += outputTokens;
                    chargedTokens += (long) inputTokens + outputTokens - reservedTokens;
                    if (legacyTracker != null) legacyTracker.record(inputTokens, outputTokens);
                } else unknownAttempts++;
            }
        }
        private Map<String,Object> snapshot() {
            Map<String,Object> data = new LinkedHashMap<>();
            data.put("attemptId", id); data.put("provider", provider); data.put("model", model);
            data.put("estimatedInputTokens", estimatedInputTokens); data.put("maxTokens", maxTokens);
            data.put("reservedTokens", reservedTokens); data.put("inputTokens", inputTokens);
            data.put("outputTokens", outputTokens); data.put("status", status.name());
            return Collections.unmodifiableMap(data);
        }
    }
}
