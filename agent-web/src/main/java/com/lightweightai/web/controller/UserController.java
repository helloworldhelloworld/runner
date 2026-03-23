package com.lightweightai.web.controller;

import com.lightweightai.user.UserService;
import com.lightweightai.user.model.EmotionRecord;
import com.lightweightai.user.model.SoulUser;
import com.lightweightai.web.service.AuthSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for user management and emotion check-ins.
 *
 * POST /user/anonymous            → create anonymous user
 * POST /user/checkin              → emotion check-in
 * GET  /user/{userId}/emotions    → recent emotions (default 7 days)
 * GET  /user/{userId}/stats       → streak + distribution
 */
@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "*")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;

    public UserController(UserService userService,
                          PasswordEncoder passwordEncoder,
                          AuthSessionService authSessionService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
    }

    @PostMapping("/anonymous")
    public ResponseEntity<SoulUser> createAnonymous() {
        SoulUser user = userService.createAnonymousUser();
        return ResponseEntity.ok(user);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
            }

            SoulUser user = userService.register(
                request.username().trim(),
                passwordEncoder.encode(request.password()),
                request.nickname()
            );
            String token = authSessionService.createSessionToken(user.getId());
            return ResponseEntity.ok(Map.of(
                "token", token,
                "user", user
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Register failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "注册失败"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
            }
            return userService.findByUsername(request.username().trim())
                .filter(u -> u.getPasswordHash() != null && passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .<ResponseEntity<?>>map(user -> {
                    String token = authSessionService.createSessionToken(user.getId());
                    return ResponseEntity.ok(Map.of("token", token, "user", user));
                })
                .orElseGet(() -> ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误")));
        } catch (Exception e) {
            logger.error("Login failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "登录失败"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(name = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "缺少登录凭证"));
        }
        String token = authorization.substring("Bearer ".length());
        return authSessionService.resolveUserId(token)
            .map(userId -> ResponseEntity.ok(Map.of("userId", userId)))
            .orElseGet(() -> ResponseEntity.status(401).body(Map.of("error", "登录态无效")));
    }

    @PostMapping("/checkin")
    public ResponseEntity<?> checkin(@RequestBody CheckinRequest request) {
        try {
            EmotionRecord record = userService.checkin(
                request.userId(), request.emotion(), request.note()
            );
            return ResponseEntity.ok(record);
        } catch (Exception e) {
            logger.error("Checkin failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{userId}/emotions")
    public ResponseEntity<List<EmotionRecord>> getEmotions(
            @PathVariable("userId") String userId,
            @RequestParam(name = "days", defaultValue = "7") int days) {
        return ResponseEntity.ok(userService.getEmotions(userId, days));
    }

    @GetMapping("/{userId}/stats")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(userService.getStats(userId));
    }

    public record CheckinRequest(String userId, String emotion, String note) {}
    public record RegisterRequest(String username, String password, String nickname) {}
    public record LoginRequest(String username, String password) {}
}
