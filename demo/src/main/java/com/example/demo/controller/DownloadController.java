package com.example.demo.controller;

import com.example.demo.dto.DownloadRequest;
import com.example.demo.model.Download;
import com.example.demo.model.Lecture;
import com.example.demo.repository.CourseRepository;
import com.example.demo.repository.DownloadRepository;
import com.example.demo.repository.LectureRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/downloads")
public class DownloadController {

    private final DownloadRepository downloadRepo;
    private final LectureRepository lectureRepo;
    private final CourseRepository courseRepo;

    public DownloadController(DownloadRepository downloadRepo, LectureRepository lectureRepo,
                              CourseRepository courseRepo) {
        this.downloadRepo = downloadRepo;
        this.lectureRepo = lectureRepo;
        this.courseRepo = courseRepo;
    }

    @GetMapping
    public List<Map<String, Object>> list(Authentication auth) {
        List<Download> items = downloadRepo.findByUserId(auth.getName(),
                Sort.by(Sort.Direction.DESC, "downloadedAt"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Download d : items) {
            Map<String, Object> m = downloadToMap(d);
            lectureRepo.findById(d.getLectureId()).ifPresent(lec -> {
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("title", lec.getTitle());
                l.put("type", lec.getType());
                l.put("duration_seconds", lec.getDurationSeconds());
                m.put("lecture", l);
            });
            courseRepo.findById(d.getCourseId()).ifPresent(c -> {
                Map<String, Object> cs = new LinkedHashMap<>();
                cs.put("title", c.getTitle());
                cs.put("thumbnail", c.getThumbnail());
                m.put("course", cs);
            });
            result.add(m);
        }
        return result;
    }

    @PostMapping
    public Map<String, String> record(@Valid @RequestBody DownloadRequest body, Authentication auth) {
        String userId = auth.getName();
        Download dl = downloadRepo.findByUserIdAndLectureId(userId, body.getLectureId())
                .orElseGet(() -> {
                    Download d = new Download();
                    d.setId(UUID.randomUUID().toString());
                    d.setUserId(userId);
                    d.setLectureId(body.getLectureId());
                    return d;
                });
        dl.setCourseId(body.getCourseId());
        dl.setSizeBytes(body.getSizeBytes());
        dl.setEncrypted(body.isEncrypted());
        dl.setDownloadedAt(Instant.now().toString());
        downloadRepo.save(dl);
        return Map.of("message", "recorded");
    }

    @DeleteMapping("/{lectureId}")
    public Map<String, String> delete(@PathVariable String lectureId, Authentication auth) {
        downloadRepo.deleteByUserIdAndLectureId(auth.getName(), lectureId);
        return Map.of("message", "deleted");
    }

    private static Map<String, Object> downloadToMap(Download d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("user_id", d.getUserId());
        m.put("lecture_id", d.getLectureId());
        m.put("course_id", d.getCourseId());
        m.put("size_bytes", d.getSizeBytes());
        m.put("encrypted", d.isEncrypted());
        m.put("downloaded_at", d.getDownloadedAt());
        return m;
    }
}
