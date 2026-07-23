package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/live-classes")
public class LiveClassController {

    private final LiveClassRepository liveClassRepo;
    private final EnrollmentRepository enrollRepo;
    private final CourseRepository courseRepo;
    private final LiveAttendanceRepository attendanceRepo;

    public LiveClassController(LiveClassRepository liveClassRepo, EnrollmentRepository enrollRepo,
                               CourseRepository courseRepo, LiveAttendanceRepository attendanceRepo) {
        this.liveClassRepo = liveClassRepo;
        this.enrollRepo = enrollRepo;
        this.courseRepo = courseRepo;
        this.attendanceRepo = attendanceRepo;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "true") boolean upcoming,
                                          Authentication auth) {
        List<Enrollment> enrollments = enrollRepo.findByUserId(auth.getName());
        if (enrollments.isEmpty()) return List.of();
        List<String> enrolledIds = enrollments.stream().map(Enrollment::getCourseId).toList();

        List<LiveClass> items;
        Sort sort = Sort.by(Sort.Direction.ASC, "startTime");
        if (upcoming) {
            items = liveClassRepo.findUpcomingForEnrolled(enrolledIds, Instant.now().toString(), sort)
                    .stream().limit(50).toList();
        } else {
            items = liveClassRepo.findAllForEnrolled(enrolledIds, sort)
                    .stream().limit(50).toList();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (LiveClass lc : items) {
            Map<String, Object> m = DashboardController.liveClassToMap(lc);
            courseRepo.findById(lc.getCourseId()).ifPresent(c -> {
                Map<String, Object> cs = new LinkedHashMap<>();
                cs.put("title", c.getTitle());
                cs.put("instructor", c.getInstructor());
                cs.put("thumbnail", c.getThumbnail());
                m.put("course", cs);
            });
            result.add(m);
        }
        return result;
    }

    @GetMapping("/{classId}")
    public Map<String, Object> get(@PathVariable String classId, Authentication auth) {
        LiveClass lc = liveClassRepo.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Live class not found"));
        Map<String, Object> m = DashboardController.liveClassToMap(lc);
        courseRepo.findById(lc.getCourseId()).ifPresent(c -> {
            Map<String, Object> cs = new LinkedHashMap<>();
            cs.put("title", c.getTitle());
            cs.put("instructor", c.getInstructor());
            cs.put("thumbnail", c.getThumbnail());
            m.put("course", cs);
        });
        boolean attended = attendanceRepo.findByUserIdAndLiveClassId(auth.getName(), classId).isPresent();
        m.put("attended", attended);
        return m;
    }

    @PostMapping("/{classId}/attend")
    public Map<String, Object> attend(@PathVariable String classId, Authentication auth) {
        LiveClass lc = liveClassRepo.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Live class not found"));
        String userId = auth.getName();
        LiveAttendance att = attendanceRepo.findByUserIdAndLiveClassId(userId, classId)
                .orElseGet(() -> {
                    LiveAttendance a = new LiveAttendance();
                    a.setId(UUID.randomUUID().toString());
                    a.setUserId(userId);
                    a.setLiveClassId(classId);
                    a.setCourseId(lc.getCourseId());
                    return a;
                });
        att.setJoinedAt(Instant.now().toString());
        attendanceRepo.save(att);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Attendance recorded");
        result.put("meet_url", lc.getMeetUrl());
        return result;
    }
}
