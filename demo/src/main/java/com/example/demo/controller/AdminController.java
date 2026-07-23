package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import jakarta.validation.Valid;
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
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepo;
    private final CourseRepository courseRepo;
    private final CategoryRepository categoryRepo;
    private final LectureRepository lectureRepo;
    private final QuizRepository quizRepo;
    private final LiveClassRepository liveClassRepo;
    private final EnrollmentRepository enrollRepo;
    private final OrderRepository orderRepo;
    private final NotificationRepository notifRepo;
    private final QuizResultRepository quizResultRepo;
    private final ProgressRepository progressRepo;
    private final MongoTemplate mongoTemplate;

    public AdminController(UserRepository userRepo, CourseRepository courseRepo,
                           CategoryRepository categoryRepo, LectureRepository lectureRepo,
                           QuizRepository quizRepo, LiveClassRepository liveClassRepo,
                           EnrollmentRepository enrollRepo, OrderRepository orderRepo,
                           NotificationRepository notifRepo, QuizResultRepository quizResultRepo,
                           ProgressRepository progressRepo, MongoTemplate mongoTemplate) {
        this.userRepo = userRepo;
        this.courseRepo = courseRepo;
        this.categoryRepo = categoryRepo;
        this.lectureRepo = lectureRepo;
        this.quizRepo = quizRepo;
        this.liveClassRepo = liveClassRepo;
        this.enrollRepo = enrollRepo;
        this.orderRepo = orderRepo;
        this.notifRepo = notifRepo;
        this.quizResultRepo = quizResultRepo;
        this.progressRepo = progressRepo;
        this.mongoTemplate = mongoTemplate;
    }

    /** Check that the authenticated user is admin, throw 403 otherwise. */
    private void requireAdmin(Authentication auth) {
        userRepo.findById(auth.getName())
                .filter(User::isAdmin)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required"));
    }

    // -------- Stats --------
    @GetMapping("/stats")
    public Map<String, Object> stats(Authentication auth) {
        requireAdmin(auth);
        long revenuePaise = orderRepo.findByStatus("paid").stream()
                .mapToLong(Order::getAmount).sum();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("students", userRepo.count() - mongoTemplate.count(
                new Query(Criteria.where("isAdmin").is(true)), User.class));
        m.put("courses", courseRepo.count());
        m.put("lectures", lectureRepo.count());
        m.put("quizzes", quizRepo.count());
        m.put("live_classes", liveClassRepo.count());
        m.put("enrollments", enrollRepo.count());
        m.put("orders_paid", orderRepo.findByStatus("paid").size());
        m.put("revenue_paise", revenuePaise);
        return m;
    }

    // -------- Courses --------
    @GetMapping("/courses")
    public List<Course> adminListCourses(Authentication auth) {
        requireAdmin(auth);
        return courseRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @PostMapping("/courses")
    public Course adminCreateCourse(@Valid @RequestBody AdminCourseRequest body, Authentication auth) {
        requireAdmin(auth);
        Course c = new Course();
        c.setId("crs-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        c.setTitle(body.getTitle());
        c.setInstructor(body.getInstructor());
        c.setInstructorBio(body.getInstructorBio());
        c.setCategoryId(body.getCategoryId());
        c.setCategory(body.getCategory());
        c.setThumbnail(body.getThumbnail());
        c.setBanner(body.getBanner());
        c.setDescription(body.getDescription());
        c.setDurationMinutes(body.getDurationMinutes());
        c.setLanguage(body.getLanguage());
        c.setLevel(body.getLevel());
        c.setPrice(body.getPrice());
        c.setDiscountPrice(body.getDiscountPrice());
        c.setRequirements(body.getRequirements());
        c.setOutcomes(body.getOutcomes());
        c.setFaqs(body.getFaqs());
        c.setCertificate(body.isCertificate());
        c.setRating(0);
        c.setStudents(0);
        c.setCreatedAt(Instant.now().toString());
        return courseRepo.save(c);
    }

    @PutMapping("/courses/{courseId}")
    public Course adminUpdateCourse(@PathVariable String courseId,
                                    @Valid @RequestBody AdminCourseRequest body, Authentication auth) {
        requireAdmin(auth);
        Course c = courseRepo.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        c.setTitle(body.getTitle()); c.setInstructor(body.getInstructor());
        c.setInstructorBio(body.getInstructorBio()); c.setCategoryId(body.getCategoryId());
        c.setCategory(body.getCategory()); c.setThumbnail(body.getThumbnail());
        c.setBanner(body.getBanner()); c.setDescription(body.getDescription());
        c.setDurationMinutes(body.getDurationMinutes()); c.setLanguage(body.getLanguage());
        c.setLevel(body.getLevel()); c.setPrice(body.getPrice());
        c.setDiscountPrice(body.getDiscountPrice()); c.setRequirements(body.getRequirements());
        c.setOutcomes(body.getOutcomes()); c.setFaqs(body.getFaqs());
        c.setCertificate(body.isCertificate());
        return courseRepo.save(c);
    }

    @DeleteMapping("/courses/{courseId}")
    public Map<String, String> adminDeleteCourse(@PathVariable String courseId, Authentication auth) {
        requireAdmin(auth);
        courseRepo.deleteById(courseId);
        mongoTemplate.remove(new Query(Criteria.where("courseId").is(courseId)), Lecture.class);
        mongoTemplate.remove(new Query(Criteria.where("courseId").is(courseId)), Quiz.class);
        mongoTemplate.remove(new Query(Criteria.where("courseId").is(courseId)), LiveClass.class);
        mongoTemplate.remove(new Query(Criteria.where("courseId").is(courseId)), Enrollment.class);
        return Map.of("message", "deleted");
    }

    // -------- Lectures --------
    @GetMapping("/lectures")
    public List<Lecture> adminListLectures(@RequestParam(required = false) String courseId,
                                            Authentication auth) {
        requireAdmin(auth);
        if (courseId != null) return lectureRepo.findByCourseIdOrderByOrderAsc(courseId);
        return lectureRepo.findAll(Sort.by(Sort.Direction.ASC, "order"));
    }

    @PostMapping("/lectures")
    public Lecture adminCreateLecture(@Valid @RequestBody AdminLectureRequest body, Authentication auth) {
        requireAdmin(auth);
        Lecture l = new Lecture();
        l.setId("lec-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        l.setCourseId(body.getCourseId()); l.setTitle(body.getTitle()); l.setType(body.getType());
        l.setUrl(body.getUrl()); l.setDurationSeconds(body.getDurationSeconds());
        l.setOrder(body.getOrder()); l.setDescription(body.getDescription()); l.setNotes(body.getNotes());
        return lectureRepo.save(l);
    }

    @PutMapping("/lectures/{lectureId}")
    public Lecture adminUpdateLecture(@PathVariable String lectureId,
                                       @Valid @RequestBody AdminLectureRequest body, Authentication auth) {
        requireAdmin(auth);
        Lecture l = lectureRepo.findById(lectureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecture not found"));
        l.setCourseId(body.getCourseId()); l.setTitle(body.getTitle()); l.setType(body.getType());
        l.setUrl(body.getUrl()); l.setDurationSeconds(body.getDurationSeconds());
        l.setOrder(body.getOrder()); l.setDescription(body.getDescription()); l.setNotes(body.getNotes());
        return lectureRepo.save(l);
    }

    @DeleteMapping("/lectures/{lectureId}")
    public Map<String, String> adminDeleteLecture(@PathVariable String lectureId, Authentication auth) {
        requireAdmin(auth);
        lectureRepo.deleteById(lectureId);
        return Map.of("message", "deleted");
    }

    // -------- Quizzes --------
    @GetMapping("/quizzes")
    public List<Quiz> adminListQuizzes(Authentication auth) {
        requireAdmin(auth);
        return quizRepo.findAll();
    }

    @PostMapping("/quizzes")
    public Quiz adminCreateQuiz(@Valid @RequestBody AdminQuizRequest body, Authentication auth) {
        requireAdmin(auth);
        Quiz q = new Quiz();
        q.setId("quiz-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        q.setCourseId(body.getCourseId()); q.setTitle(body.getTitle());
        q.setDurationMinutes(body.getDurationMinutes());
        List<Map<String, Object>> questions = body.getQuestions() != null ? body.getQuestions() : List.of();
        for (Map<String, Object> question : questions) {
            question.putIfAbsent("id", "q-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6));
        }
        q.setQuestions(questions);
        return quizRepo.save(q);
    }

    @PutMapping("/quizzes/{quizId}")
    public Quiz adminUpdateQuiz(@PathVariable String quizId,
                                 @Valid @RequestBody AdminQuizRequest body, Authentication auth) {
        requireAdmin(auth);
        Quiz q = quizRepo.findById(quizId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found"));
        q.setCourseId(body.getCourseId()); q.setTitle(body.getTitle());
        q.setDurationMinutes(body.getDurationMinutes());
        List<Map<String, Object>> questions = body.getQuestions() != null ? body.getQuestions() : List.of();
        for (Map<String, Object> question : questions) {
            question.putIfAbsent("id", "q-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6));
        }
        q.setQuestions(questions);
        return quizRepo.save(q);
    }

    @DeleteMapping("/quizzes/{quizId}")
    public Map<String, String> adminDeleteQuiz(@PathVariable String quizId, Authentication auth) {
        requireAdmin(auth);
        quizRepo.deleteById(quizId);
        return Map.of("message", "deleted");
    }

    // -------- Students --------
    @GetMapping("/students")
    public List<Map<String, Object>> adminListStudents(
            @RequestParam(required = false) String q, Authentication auth) {
        requireAdmin(auth);
        Query query = new Query(Criteria.where("isAdmin").ne(true))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(500);
        if (q != null && !q.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("email").regex(q, "i"),
                    Criteria.where("name").regex(q, "i")
            ));
        }
        // Re-create query without conflicting criteria by using mongoTemplate directly
        Query baseQ = new Query(Criteria.where("isAdmin").ne(true))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(500);
        List<User> users = mongoTemplate.find(baseQ, User.class);
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            if (q != null && !q.isBlank()) {
                boolean match = u.getEmail().toLowerCase().contains(q.toLowerCase())
                        || u.getName().toLowerCase().contains(q.toLowerCase());
                if (!match) continue;
            }
            Map<String, Object> m = userToAdminMap(u);
            m.put("enrollments_count", enrollRepo.countByUserId(u.getId()));
            result.add(m);
        }
        return result;
    }

    @GetMapping("/students/{userId}")
    public Map<String, Object> adminGetStudent(@PathVariable String userId, Authentication auth) {
        requireAdmin(auth);
        User u = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        Map<String, Object> m = userToAdminMap(u);

        List<Enrollment> enrolls = enrollRepo.findByUserId(userId);
        List<Map<String, Object>> enrollList = new ArrayList<>();
        for (Enrollment e : enrolls) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("id", e.getId()); em.put("user_id", e.getUserId());
            em.put("course_id", e.getCourseId()); em.put("enrolled_at", e.getEnrolledAt());
            courseRepo.findById(e.getCourseId()).ifPresent(c -> {
                Map<String, Object> cs = new LinkedHashMap<>();
                cs.put("title", c.getTitle()); cs.put("thumbnail", c.getThumbnail());
                em.put("course", cs);
            });
            enrollList.add(em);
        }
        m.put("enrollments", enrollList);
        m.put("quiz_results", quizResultRepo.findByUserIdOrderBySubmittedAtDesc(userId)
                .stream().limit(20).toList());
        return m;
    }

    @PatchMapping("/students/{userId}")
    public Map<String, Object> adminUpdateStudent(@PathVariable String userId,
                                                   @RequestBody Map<String, Object> body,
                                                   Authentication auth) {
        requireAdmin(auth);
        User u = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        if (body.containsKey("disabled")) u.setDisabled((Boolean) body.get("disabled"));
        if (body.containsKey("name")) u.setName((String) body.get("name"));
        if (body.containsKey("is_admin")) u.setAdmin((Boolean) body.get("is_admin"));
        userRepo.save(u);
        return userToAdminMap(u);
    }

    // -------- Live Classes --------
    @GetMapping("/live-classes")
    public List<LiveClass> adminListLive(Authentication auth) {
        requireAdmin(auth);
        return liveClassRepo.findAll(Sort.by(Sort.Direction.ASC, "startTime"));
    }

    @PostMapping("/live-classes")
    public LiveClass adminCreateLive(@Valid @RequestBody AdminLiveRequest body, Authentication auth) {
        requireAdmin(auth);
        LiveClass lc = new LiveClass();
        lc.setId("live-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        applyLiveFields(lc, body);
        return liveClassRepo.save(lc);
    }

    @PutMapping("/live-classes/{classId}")
    public LiveClass adminUpdateLive(@PathVariable String classId,
                                      @Valid @RequestBody AdminLiveRequest body, Authentication auth) {
        requireAdmin(auth);
        LiveClass lc = liveClassRepo.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Live class not found"));
        applyLiveFields(lc, body);
        return liveClassRepo.save(lc);
    }

    @DeleteMapping("/live-classes/{classId}")
    public Map<String, String> adminDeleteLive(@PathVariable String classId, Authentication auth) {
        requireAdmin(auth);
        liveClassRepo.deleteById(classId);
        return Map.of("message", "deleted");
    }

    private void applyLiveFields(LiveClass lc, AdminLiveRequest body) {
        lc.setCourseId(body.getCourseId()); lc.setTitle(body.getTitle());
        lc.setDescription(body.getDescription()); lc.setMeetUrl(body.getMeetUrl());
        lc.setStartTime(body.getStartTime()); lc.setEndTime(body.getEndTime());
        lc.setDurationMinutes(body.getDurationMinutes()); lc.setInstructor(body.getInstructor());
        lc.setRecordingUrl(body.getRecordingUrl());
    }

    // -------- Broadcast / Notifications --------
    @PostMapping("/broadcast")
    public Notification adminBroadcast(@Valid @RequestBody AdminBroadcastRequest body, Authentication auth) {
        requireAdmin(auth);
        Notification n = new Notification();
        n.setId(UUID.randomUUID().toString());
        n.setTitle(body.getTitle()); n.setBody(body.getBody()); n.setType(body.getType());
        n.setBroadcast(body.getUserId() == null); n.setUserId(body.getUserId());
        n.setCreatedAt(Instant.now().toString());
        return notifRepo.save(n);
    }

    @GetMapping("/notifications")
    public List<Notification> adminListNotifications(Authentication auth) {
        requireAdmin(auth);
        return notifRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream().limit(500).toList();
    }

    @DeleteMapping("/notifications/{notifId}")
    public Map<String, String> adminDeleteNotification(@PathVariable String notifId, Authentication auth) {
        requireAdmin(auth);
        notifRepo.deleteById(notifId);
        return Map.of("message", "deleted");
    }

    // -------- Orders / Revenue --------
    @GetMapping("/orders")
    public List<Map<String, Object>> adminListOrders(
            @RequestParam(required = false) String status, Authentication auth) {
        requireAdmin(auth);
        List<Order> orders = status != null
                ? orderRepo.findByStatus(status)
                : orderRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Order o : orders) {
            Map<String, Object> m = PaymentController.orderToMap(o);
            userRepo.findById(o.getUserId()).ifPresent(u -> {
                Map<String, Object> um = new LinkedHashMap<>();
                um.put("email", u.getEmail()); um.put("name", u.getName());
                m.put("user", um);
            });
            courseRepo.findById(o.getCourseId()).ifPresent(c -> {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("title", c.getTitle()); cm.put("thumbnail", c.getThumbnail());
                m.put("course", cm);
            });
            result.add(m);
        }
        return result;
    }

    // -------- Categories --------
    @GetMapping("/categories")
    public List<Category> adminListCategories(Authentication auth) {
        requireAdmin(auth);
        return categoryRepo.findAll();
    }

    // -------- Helpers --------
    private Map<String, Object> userToAdminMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId()); m.put("email", u.getEmail()); m.put("name", u.getName());
        m.put("avatar", u.getAvatar()); m.put("phone", u.getPhone());
        m.put("disabled", u.isDisabled()); m.put("is_admin", u.isAdmin());
        m.put("created_at", u.getCreatedAt());
        return m;
    }
}
