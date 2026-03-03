package com.lightweightai.web.controller;

import com.lightweightai.user.UserService;
import com.lightweightai.user.model.EmotionRecord;
import com.lightweightai.user.model.SoulUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
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
                request.getUserId(), request.getEmotion(), request.getNote()
            );
            return ResponseEntity.ok(record);
        } catch (Exception e) {
            logger.error("Checkin failed", e);
            return ResponseEntity.status(500).body(Collections.singletonMap("error", e.getMessage()));
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

    public static class CheckinRequest {
        private String userId;
        private String emotion;
        private String note;

        public CheckinRequest() {}

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getEmotion() { return emotion; }
        public void setEmotion(String emotion) { this.emotion = emotion; }

        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }
}
