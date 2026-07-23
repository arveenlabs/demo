package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    @Value("${emergent.push.base-url}")
    private String baseUrl;

    @Value("${emergent.push.key}")
    private String pushKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendPush(List<String> recipients, Map<String, String> data, String idempotencyKey) {
        if (recipients == null || recipients.isEmpty()) return;
        if (recipients.size() > 100) throw new IllegalArgumentException("max 100 recipients per push");
        if (!data.containsKey("title") || !data.containsKey("message")) {
            throw new IllegalArgumentException("data must include title and message");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("recipients", recipients);
        payload.put("data", data);
        if (idempotencyKey != null) payload.put("$idempotency_key", idempotencyKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Push-Key", pushKey);

        try {
            restTemplate.exchange(
                    baseUrl + "/api/v1/push/trigger",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    Void.class);
        } catch (Exception e) {
            log.warn("push send failed: {}", e.getMessage());
        }
    }
}
