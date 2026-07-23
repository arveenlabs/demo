package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final ProgressRepository progressRepo;
    private final CourseRepository courseRepo;
    private final LectureRepository lectureRepo;
    private final EnrollmentRepository enrollRepo;
    private final QuizResultRepository quizResultRepo;
    private final LiveClassRepository liveClassRepo;
    private final MongoTemplate mongoTemplate;

    public DashboardController(ProgressRepository progressRepo, CourseRepository courseRepo,
                               LectureRepository lectureRepo, EnrollmentRepository enrollRepo,
                               QuizResultRepository quizResultRepo, LiveClassRepository liveClassRepo,
                               MongoTemplate mongoTemplate) {
        this.progressRepo = progressRepo;
        this.courseRepo = courseRepo;
        this.lectureRepo = lectureRepo;
        this.enrollRepo = enrollRepo;
        this.quizResultRepo = quizResultRepo;
        this.liveClassRepo = liveClassRepo;
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(Authentication auth) {
        String userId = auth.getName();

        // Continue learning — 3 most recently updated progress entries
        List<Progress> recent = progressRepo.findByUserId(userId,
                Sort.by(Sort.Direction.DESC, "updatedAt"))
                .stream().limit(3).toList();

        List<Map<String, Object>> continueLearning = new ArrayList<>();
        for (Progress p : recent) {
            courseRepo.findById(p.getCourseId()).ifPresent(c -> {
                long total = lectureRepo.countByCourseId(c.getId());
                long comp = progressRepo.countByUserIdAndCourseIdAndCompleted(userId, c.getId(), true);
                Map<String, Object> cm = CourseController.courseToMap(c);
                cm.put("progress_percent", total > 0 ? (int) Math.round((comp * 100.0) / total) : 0);
                cm.put("last_lecture_id", p.getLectureId());
                continueLearning.add(cm);
            });
        }

        // Stats
        long totalEnrolled = enrollRepo.countByUserId(userId);
        List<Enrollment> enrollments = enrollRepo.findByUserId(userId);
        long completedCoursesCount = 0;
        for (Enrollment e : enrollments) {
            long total = lectureRepo.countByCourseId(e.getCourseId());
            long comp = progressRepo.countByUserIdAndCourseIdAndCompleted(userId, e.getCourseId(), true);
            if (total > 0 && comp == total) completedCoursesCount++;
        }
        long quizCount = quizResultRepo.countByUserId(userId);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("enrolled", totalEnrolled);
        stats.put("completed_courses", completedCoursesCount);
        stats.put("certificates", completedCoursesCount);
        stats.put("quizzes_taken", quizCount);

        // Upcoming live classes (enrolled courses only)
        List<String> enrolledIds = enrollments.stream().map(Enrollment::getCourseId).toList();
        List<Map<String, Object>> upcomingLive = new ArrayList<>();
        if (!enrolledIds.isEmpty()) {
            String nowIso = Instant.now().toString();
            List<LiveClass> live = liveClassRepo.findUpcomingByCourseIdIn(
                    enrolledIds, nowIso, Sort.by(Sort.Direction.ASC, "startTime"))
                    .stream().limit(3).toList();
            for (LiveClass lc : live) {
                Map<String, Object> m = liveClassToMap(lc);
                courseRepo.findById(lc.getCourseId()).ifPresent(c -> {
                    Map<String, Object> cs = new LinkedHashMap<>();
                    cs.put("id", c.getId());
                    cs.put("title", c.getTitle());
                    cs.put("thumbnail", c.getThumbnail());
                    cs.put("instructor", c.getInstructor());
                    m.put("course", cs);
                });
                upcomingLive.add(m);
            }
        }

        // Weekly learning (progress updates per day, last 7 days)
        List<Map<String, Object>> week = new ArrayList<>();
        Instant now = Instant.now();
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEE").withZone(ZoneOffset.UTC);
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
        for (int i = 6; i >= 0; i--) {
            Instant day = now.minusSeconds((long) i * 86400);
            String datePrefix = dateFmt.format(day);
            long count = progressRepo.countByUserIdAndUpdatedAtStartingWith(userId, datePrefix);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("day", dayFmt.format(day));
            d.put("minutes", count * 12);
            week.add(d);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("continue_learning", continueLearning);
        result.put("stats", stats);
        result.put("weekly_learning", week);
        result.put("upcoming_live_classes", upcomingLive);
        return result;
    }

    static Map<String, Object> liveClassToMap(LiveClass lc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", lc.getId());
        m.put("course_id", lc.getCourseId());
        m.put("title", lc.getTitle());
        m.put("description", lc.getDescription());
        m.put("meet_url", lc.getMeetUrl());
        m.put("start_time", lc.getStartTime());
        m.put("end_time", lc.getEndTime());
        m.put("duration_minutes", lc.getDurationMinutes());
        m.put("instructor", lc.getInstructor());
        m.put("recording_url", lc.getRecordingUrl());
        return m;
    }
}
