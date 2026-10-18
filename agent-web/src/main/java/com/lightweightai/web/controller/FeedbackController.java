package com.lightweightai.web.controller;

import com.lightweightai.kernel.feedback.FeedbackIndex;
import com.lightweightai.kernel.feedback.FeedbackRecord;
import com.lightweightai.web.service.AuthenticatedRequests;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/feedback")
public final class FeedbackController {
    private final FeedbackIndex index;

    public FeedbackController(FeedbackIndex index) { this.index = index; }

    @PostMapping
    public FeedbackRecord append(@RequestBody Submit body) {
        return index.append(new FeedbackRecord(
                body.feedbackId() == null ? UUID.randomUUID().toString() : body.feedbackId(),
                principal(), body.source(), body.taskId(), body.runId(), body.candidateDigest(),
                body.evaluationDigest(), body.kind(), body.text(), body.attributes(), System.currentTimeMillis()));
    }

    @GetMapping
    public List<FeedbackRecord> search(@RequestParam(required = false) String candidateDigest,
                                       @RequestParam(required = false) String kind) {
        return index.search(candidateDigest, kind).stream().filter(f -> principal().equals(f.principalId())).toList();
    }

    private String principal() {
        String value = AuthenticatedRequests.currentPrincipal();
        return value == null ? "anonymous" : value;
    }

    public record Submit(String feedbackId, String source, String taskId, String runId,
                         String candidateDigest, String evaluationDigest, String kind,
                         String text, Map<String, Object> attributes) {}
}
