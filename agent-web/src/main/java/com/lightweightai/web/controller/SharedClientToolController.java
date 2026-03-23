package com.lightweightai.web.controller;

import com.lightweightai.web.model.SharedClientTool;
import com.lightweightai.web.service.AuthSessionService;
import com.lightweightai.web.service.SharedClientToolService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/client-tools")
@CrossOrigin(origins = "*")
public class SharedClientToolController {

    private final SharedClientToolService sharedClientToolService;
    private final AuthSessionService authSessionService;

    public SharedClientToolController(SharedClientToolService sharedClientToolService,
                                      AuthSessionService authSessionService) {
        this.sharedClientToolService = sharedClientToolService;
        this.authSessionService = authSessionService;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(name = "Authorization", required = false) String authorization) {
        String userId = resolveUserIdOrAnonymous(authorization);
        List<SharedClientTool> tools = sharedClientToolService.list();
        return ResponseEntity.ok(Map.of("viewer", userId, "tools", tools));
    }

    @PutMapping("/{key}")
    public ResponseEntity<?> upsert(@PathVariable("key") String key,
                                    @RequestBody SharedClientTool payload,
                                    @RequestHeader(name = "Authorization", required = false) String authorization) {
        String userId = resolveUserIdOrAnonymous(authorization);
        SharedClientTool saved = sharedClientToolService.upsert(key, payload, userId);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<?> remove(@PathVariable("key") String key,
                                    @RequestHeader(name = "Authorization", required = false) String authorization) {
        String userId = resolveUserIdOrAnonymous(authorization);
        boolean removed = sharedClientToolService.remove(key);
        return ResponseEntity.ok(Map.of("removed", removed, "operator", userId));
    }

    private String resolveUserIdOrAnonymous(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "anonymous";
        }
        String token = authorization.substring("Bearer ".length());
        return authSessionService.resolveUserId(token).orElse("anonymous");
    }
}
