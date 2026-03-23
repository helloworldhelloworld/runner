package com.lightweightai.web.controller;

import com.lightweightai.user.UserService;
import com.lightweightai.user.model.EmotionRecord;
import com.lightweightai.user.model.SoulUser;
import com.lightweightai.web.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for user management, authentication, and emotion check-ins.
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
            String token = authSessionService.createSessionToken(user.getId(), user.getRole());
            return ResponseEntity.ok(Map.of(
                "token", token,
                "user", userToMap(user)
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
                    if (!user.isEnabled()) {
                        return ResponseEntity.status(403).body(Map.of("error", "账户已被禁用"));
                    }
                    String token = authSessionService.createSessionToken(user.getId(), user.getRole());
                    return ResponseEntity.ok(Map.of("token", token, "user", userToMap(user)));
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
        return authSessionService.resolveSession(token)
            .flatMap(session -> userService.findById(session.getUserId()))
            .map(user -> ResponseEntity.ok((Object) userToMap(user)))
            .orElseGet(() -> ResponseEntity.status(401).body(Map.of("error", "登录态无效")));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(name = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authSessionService.invalidate(authorization.substring("Bearer ".length()));
        }
        return ResponseEntity.ok(Map.of("message", "已退出登录"));
    }

    // ========== Admin Endpoints ==========

    @GetMapping("/list")
    public ResponseEntity<?> listUsers(HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        }
        List<SoulUser> users = userService.listAllUsers();
        List<Map<String, Object>> result = users.stream().map(this::userToMap).toList();
        return ResponseEntity.ok(Map.of("users", result, "total", result.size()));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable("id") String id,
                                        @RequestBody Map<String, String> body,
                                        HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        }
        try {
            String role = body.get("role");
            userService.updateUserRole(id, role);
            return ResponseEntity.ok(Map.of("message", "角色已更新"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Update role failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "更新失败"));
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable("id") String id,
                                          @RequestBody Map<String, Object> body,
                                          HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        }
        try {
            Object enabledObj = body.get("enabled");
            boolean enabled = enabledObj instanceof Boolean ? (Boolean) enabledObj : Boolean.parseBoolean(String.valueOf(enabledObj));
            if (enabled) {
                userService.enableUser(id);
            } else {
                userService.disableUser(id);
            }
            return ResponseEntity.ok(Map.of("message", enabled ? "已启用" : "已禁用"));
        } catch (Exception e) {
            logger.error("Update status failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "更新失败"));
        }
    }

    // ========== Emotion ==========

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

    // ========== Helpers ==========

    private boolean isAdmin(HttpServletRequest request) {
        return "ADMIN".equals(request.getAttribute("userRole"));
    }

    private Map<String, Object> userToMap(SoulUser user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("role", user.getRole());
        map.put("enabled", user.isEnabled());
        map.put("memberLevel", user.getMemberLevel());
        map.put("createdAt", user.getCreatedAt());
        map.put("lastActive", user.getLastActive());
        return map;
    }

    public record CheckinRequest(String userId, String emotion, String note) {}
    public record RegisterRequest(String username, String password, String nickname) {}
    public record LoginRequest(String username, String password) {}
}
