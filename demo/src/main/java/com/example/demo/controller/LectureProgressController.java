package com.example.demo.controller;

import com.example.demo.dto.ProgressRequest;
import com.example.demo.model.Lecture;
import com.example.demo.model.Progress;
import com.example.demo.repository.LectureRepository;
import com.example.demo.repository.ProgressRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class LectureProgressController {

    private final LectureRepository lectureRepo;
    private final ProgressRepository progressRepo;

    public LectureProgressController(LectureRepository lectureRepo, ProgressRepository progressRepo) {
        this.lectureRepo = lectureRepo;
        this.progressRepo = progressRepo;
    }

    @GetMapping("/lectures/{lectureId}")
    public Map<String, Object> getLecture(@PathVariable String lectureId, Authentication auth) {
        Lecture lec = lectureRepo.findById(lectureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecture not found"));

        Progress prog = progressRepo.findByUserIdAndLectureId(auth.getName(), lectureId).orElse(null);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", lec.getId());
        m.put("course_id", lec.getCourseId());
        m.put("title", lec.getTitle());
        m.put("type", lec.getType());
        m.put("url", lec.getUrl());
        m.put("duration_seconds", lec.getDurationSeconds());
        m.put("order", lec.getOrder());
        m.put("description", lec.getDescription());
        m.put("notes", lec.getNotes());
        m.put("watched_seconds", prog != null ? prog.getWatchedSeconds() : 0);
        m.put("completed", prog != null && prog.isCompleted());
        return m;
    }

    @PostMapping("/progress")
    public Map<String, String> saveProgress(@Valid @RequestBody ProgressRequest body, Authentication auth) {
        String userId = auth.getName();
        Progress prog = progressRepo.findByUserIdAndLectureId(userId, body.getLectureId())
                .orElseGet(() -> {
                    Progress p = new Progress();
                    p.setId(UUID.randomUUID().toString());
                    p.setUserId(userId);
                    p.setCourseId(body.getCourseId());
                    p.setLectureId(body.getLectureId());
                    return p;
                });
        prog.setWatchedSeconds(body.getWatchedSeconds());
        prog.setCompleted(body.isCompleted());
        prog.setUpdatedAt(Instant.now().toString());
        progressRepo.save(prog);
        return Map.of("message", "saved");
    }
}
