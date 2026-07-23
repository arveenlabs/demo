package com.example.demo.controller;

import com.example.demo.model.Notification;
import com.example.demo.model.NotificationRead;
import com.example.demo.repository.NotificationReadRepository;
import com.example.demo.repository.NotificationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notifRepo;
    private final NotificationReadRepository readRepo;

    public NotificationController(NotificationRepository notifRepo, NotificationReadRepository readRepo) {
        this.notifRepo = notifRepo;
        this.readRepo = readRepo;
    }

    @GetMapping
    public List<Map<String, Object>> list(Authentication auth) {
        String userId = auth.getName();
        List<Notification> items = notifRepo.findForUser(userId,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        List<NotificationRead> reads = readRepo.findByUserId(userId);
        Set<String> readIds = new HashSet<>();
        for (NotificationRead r : reads) readIds.add(r.getNotificationId());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Notification n : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("body", n.getBody());
            m.put("type", n.getType());
            m.put("broadcast", n.isBroadcast());
            m.put("user_id", n.getUserId());
            m.put("created_at", n.getCreatedAt());
            m.put("read", readIds.contains(n.getId()));
            result.add(m);
        }
        return result;
    }

    @PostMapping("/{notifId}/read")
    public Map<String, String> markRead(@PathVariable String notifId, Authentication auth) {
        String userId = auth.getName();
        NotificationRead nr = readRepo.findByUserIdAndNotificationId(userId, notifId)
                .orElseGet(() -> {
                    NotificationRead r = new NotificationRead();
                    r.setId(UUID.randomUUID().toString());
                    r.setUserId(userId);
                    r.setNotificationId(notifId);
                    return r;
                });
        nr.setReadAt(Instant.now().toString());
        readRepo.save(nr);
        return Map.of("message", "marked read");
    }
}
