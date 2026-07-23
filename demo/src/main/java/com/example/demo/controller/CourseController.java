package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
public class CourseController {

    private final CourseRepository courseRepo;
    private final CategoryRepository categoryRepo;
    private final EnrollmentRepository enrollRepo;
    private final WishlistRepository wishlistRepo;
    private final LectureRepository lectureRepo;
    private final ProgressRepository progressRepo;
    private final MongoTemplate mongoTemplate;

    public CourseController(CourseRepository courseRepo, CategoryRepository categoryRepo,
                            EnrollmentRepository enrollRepo, WishlistRepository wishlistRepo,
                            LectureRepository lectureRepo, ProgressRepository progressRepo,
                            MongoTemplate mongoTemplate) {
        this.courseRepo = courseRepo;
        this.categoryRepo = categoryRepo;
        this.enrollRepo = enrollRepo;
        this.wishlistRepo = wishlistRepo;
        this.lectureRepo = lectureRepo;
        this.progressRepo = progressRepo;
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping("/categories")
    public List<Category> listCategories() {
        return categoryRepo.findAll();
    }

    @GetMapping("/courses")
    public List<Course> listCourses(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "trending") String sort,
            @RequestParam(defaultValue = "50") int limit) {

        String sortKey = switch (sort) {
            case "latest" -> "createdAt";
            case "rating" -> "rating";
            default -> "students";
        };

        Query query = new Query().with(Sort.by(Sort.Direction.DESC, sortKey)).limit(limit);

        if (category != null && !category.equalsIgnoreCase("all")) {
            query.addCriteria(Criteria.where("categoryId").is(category));
        }
        if (q != null && !q.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("title").regex(q, "i"),
                    Criteria.where("instructor").regex(q, "i")
            ));
        }
        return mongoTemplate.find(query, Course.class);
    }

    @GetMapping("/courses/recommended")
    public List<Course> recommendedCourses() {
        return courseRepo.findAll(Sort.by(Sort.Direction.DESC, "rating"))
                .stream().limit(6).toList();
    }

    @GetMapping("/courses/trending")
    public List<Course> trendingCourses() {
        return courseRepo.findAll(Sort.by(Sort.Direction.DESC, "students"))
                .stream().limit(6).toList();
    }

    @GetMapping("/courses/{courseId}")
    public Map<String, Object> getCourse(@PathVariable String courseId, Authentication auth) {
        Course course = courseRepo.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        String userId = auth.getName();
        boolean enrolled = enrollRepo.findByUserIdAndCourseId(userId, courseId).isPresent();
        boolean wishlisted = wishlistRepo.findByUserIdAndCourseId(userId, courseId).isPresent();

        List<Lecture> lectures = lectureRepo.findByCourseIdOrderByOrderAsc(courseId);
        List<Progress> progresses = progressRepo.findByUserIdAndCourseId(userId, courseId);
        Map<String, Progress> progMap = new HashMap<>();
        for (Progress p : progresses) progMap.put(p.getLectureId(), p);

        long completed = progresses.stream().filter(Progress::isCompleted).count();
        int total = lectures.size();
        int percent = total > 0 ? (int) Math.round((completed * 100.0) / total) : 0;

        List<Map<String, Object>> lectureList = new ArrayList<>();
        for (Lecture lec : lectures) {
            Map<String, Object> l = lectureToMap(lec);
            Progress p = progMap.get(lec.getId());
            l.put("watched_seconds", p != null ? p.getWatchedSeconds() : 0);
            l.put("completed", p != null && p.isCompleted());
            lectureList.add(l);
        }

        Map<String, Object> result = courseToMap(course);
        result.put("lectures", lectureList);
        result.put("enrolled", enrolled);
        result.put("wishlisted", wishlisted);
        result.put("progress_percent", percent);
        result.put("completed_lectures", completed);
        result.put("total_lectures", lectures.size());
        return result;
    }

    @PostMapping("/courses/{courseId}/enroll")
    public Map<String, String> enroll(@PathVariable String courseId, Authentication auth) {
        Course course = courseRepo.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        String userId = auth.getName();
        if (enrollRepo.findByUserIdAndCourseId(userId, courseId).isPresent()) {
            return Map.of("message", "Already enrolled");
        }
        Enrollment e = new Enrollment();
        e.setId(UUID.randomUUID().toString());
        e.setUserId(userId);
        e.setCourseId(courseId);
        e.setEnrolledAt(Instant.now().toString());
        enrollRepo.save(e);
        course.setStudents(course.getStudents() + 1);
        courseRepo.save(course);
        return Map.of("message", "Enrolled successfully");
    }

    @GetMapping("/my/courses")
    public List<Map<String, Object>> myCourses(Authentication auth) {
        String userId = auth.getName();
        List<Enrollment> enrollments = enrollRepo.findByUserId(userId);
        if (enrollments.isEmpty()) return List.of();

        List<String> courseIds = enrollments.stream().map(Enrollment::getCourseId).toList();
        List<Course> courses = mongoTemplate.find(
                new Query(Criteria.where("_id").in(courseIds)), Course.class);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Course c : courses) {
            long total = lectureRepo.countByCourseId(c.getId());
            long comp = progressRepo.countByUserIdAndCourseIdAndCompleted(userId, c.getId(), true);
            Map<String, Object> m = courseToMap(c);
            m.put("progress_percent", total > 0 ? (int) Math.round((comp * 100.0) / total) : 0);
            result.add(m);
        }
        return result;
    }

    // ---------- Helpers ----------
    public static Map<String, Object> courseToMap(Course c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("title", c.getTitle());
        m.put("instructor", c.getInstructor());
        m.put("instructor_bio", c.getInstructorBio());
        m.put("category_id", c.getCategoryId());
        m.put("category", c.getCategory());
        m.put("thumbnail", c.getThumbnail());
        m.put("banner", c.getBanner());
        m.put("description", c.getDescription());
        m.put("duration_minutes", c.getDurationMinutes());
        m.put("language", c.getLanguage());
        m.put("level", c.getLevel());
        m.put("rating", c.getRating());
        m.put("students", c.getStudents());
        m.put("price", c.getPrice());
        m.put("discount_price", c.getDiscountPrice());
        m.put("requirements", c.getRequirements());
        m.put("outcomes", c.getOutcomes());
        m.put("faqs", c.getFaqs());
        m.put("certificate", c.isCertificate());
        m.put("created_at", c.getCreatedAt());
        return m;
    }

    private static Map<String, Object> lectureToMap(Lecture l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("course_id", l.getCourseId());
        m.put("title", l.getTitle());
        m.put("type", l.getType());
        m.put("url", l.getUrl());
        m.put("duration_seconds", l.getDurationSeconds());
        m.put("order", l.getOrder());
        m.put("description", l.getDescription());
        m.put("notes", l.getNotes());
        return m;
    }
}
