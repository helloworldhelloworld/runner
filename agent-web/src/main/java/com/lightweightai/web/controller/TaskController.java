package com.lightweightai.web.controller;

import com.lightweightai.kernel.task.DurableTask;
import com.lightweightai.kernel.task.DurableTaskService;
import com.lightweightai.web.service.AuthenticatedRequests;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public final class TaskController {
    private final DurableTaskService tasks;

    public TaskController(DurableTaskService tasks) { this.tasks = tasks; }

    @PostMapping
    public DurableTask create(@RequestBody CreateTask body) {
        return tasks.create(principal(), body.idempotencyKey(), body.goal());
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<DurableTask> get(@PathVariable String taskId) {
        return tasks.get(taskId).filter(t -> principal().equals(t.principalId()))
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{taskId}/start")
    public DurableTask start(@PathVariable String taskId) { requireOwned(taskId); return tasks.start(taskId); }

    @PostMapping("/{taskId}/checkpoint")
    public DurableTask checkpoint(@PathVariable String taskId, @RequestBody Checkpoint body) {
        requireOwned(taskId); return tasks.checkpoint(taskId, body.stepId(), body.nextAction(), body.data());
    }

    @PostMapping("/{taskId}/complete")
    public DurableTask complete(@PathVariable String taskId, @RequestBody Map<String, Object> result) {
        requireOwned(taskId); return tasks.succeed(taskId, result);
    }

    private DurableTask owned(String taskId, DurableTask result) {
        if (!principal().equals(result.principalId())) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "task not found");
        return result;
    }

    private DurableTask requireOwned(String taskId) {
        return tasks.get(taskId).filter(t -> principal().equals(t.principalId()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "task not found"));
    }

    private String principal() {
        String value = AuthenticatedRequests.currentPrincipal();
        return value == null ? "anonymous" : value;
    }

    public record CreateTask(String idempotencyKey, String goal) {}
    public record Checkpoint(String stepId, String nextAction, Map<String, Object> data) {}
}
