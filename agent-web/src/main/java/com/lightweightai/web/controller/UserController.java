package com.lightweightai.web.controller;

import com.lightweightai.user.UserService;
import com.lightweightai.user.model.EmotionRecord;
import com.lightweightai.user.model.SoulUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
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

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/anonymous")
    public ResponseEntity<SoulUser> createAnonymous() {
        SoulUser user = userService.createAnonymousUser();
        return ResponseEntity.ok(user);
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
            @PathVariable String userId,
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(userService.getEmotions(userId, days));
    }

    @GetMapping("/{userId}/stats")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getStats(userId));
    }

    public record CheckinRequest(String userId, String emotion, String note) {}
}
