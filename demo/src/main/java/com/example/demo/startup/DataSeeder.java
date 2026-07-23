package com.example.demo.startup;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CategoryRepository categoryRepo;
    private final CourseRepository courseRepo;
    private final LectureRepository lectureRepo;
    private final QuizRepository quizRepo;
    private final NotificationRepository notifRepo;
    private final LiveClassRepository liveClassRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(CategoryRepository categoryRepo, CourseRepository courseRepo,
                      LectureRepository lectureRepo, QuizRepository quizRepo,
                      NotificationRepository notifRepo, LiveClassRepository liveClassRepo,
                      UserRepository userRepo, PasswordEncoder passwordEncoder) {
        this.categoryRepo = categoryRepo;
        this.courseRepo = courseRepo;
        this.lectureRepo = lectureRepo;
        this.quizRepo = quizRepo;
        this.notifRepo = notifRepo;
        this.liveClassRepo = liveClassRepo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            seedAdmin();
            seedData();
        } catch (Exception e) {
            log.warn("Seed skipped — MongoDB not available: {}", e.getMessage());
        }
    }

    private void seedAdmin() {
        String adminEmail = "admin@lumina.com";
        userRepo.findByEmail(adminEmail).ifPresentOrElse(u -> {
            if (!u.isAdmin()) { u.setAdmin(true); userRepo.save(u); }
        }, () -> {
            User admin = new User();
            admin.setId(UUID.randomUUID().toString());
            admin.setEmail(adminEmail);
            admin.setName("Lumina Admin");
            admin.setPasswordHash(passwordEncoder.encode("Admin1234"));
            admin.setDisabled(false);
            admin.setAdmin(true);
            admin.setCreatedAt(Instant.now().toString());
            userRepo.save(admin);
            log.info("Bootstrap admin created: {} / Admin1234", adminEmail);
        });
    }

    private void seedData() {
        if (courseRepo.count() > 0) {
            seedLiveClasses(); // idempotent
            return;
        }
        log.info("Seeding LMS demo data...");

        // Categories
        List<Category> categories = List.of(
            cat("cat-dev", "Development", "code"),
            cat("cat-design", "Design", "palette"),
            cat("cat-data", "Data Science", "chart-line"),
            cat("cat-biz", "Business", "briefcase"),
            cat("cat-mkt", "Marketing", "megaphone"),
            cat("cat-lang", "Languages", "translate")
        );
        categoryRepo.saveAll(categories);

        Instant now = Instant.now();
        String sampleVideo = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
        String samplePdf = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf";

        List<Course> courses = List.of(
            course("crs-react-native", "React Native Masterclass", "Sarah Chen",
                "Senior mobile engineer at a Fortune 500 company with 10+ years of experience.",
                "cat-dev", "Development",
                "https://images.pexels.com/photos/11035380/pexels-photo-11035380.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=800",
                "https://images.pexels.com/photos/11035380/pexels-photo-11035380.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=1200",
                "Build production-ready mobile apps with React Native and Expo. Learn navigation, state management, native modules, and deployment.",
                720, "English", "Intermediate", 4.8, 12530, 89.99, 39.99,
                List.of("Basic JavaScript", "React fundamentals"),
                List.of("Build cross-platform apps", "Publish to App Store & Play Store", "Master Expo Router"),
                List.of(Map.of("q", "Do I need a Mac?", "a", "No, Expo works on Windows, Mac, and Linux.")),
                true, now),
            course("crs-ui-design", "UI/UX Design Fundamentals", "Marcus Lee",
                "Design lead who has shipped products used by millions.",
                "cat-design", "Design",
                "https://images.pexels.com/photos/196644/pexels-photo-196644.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=800",
                "https://images.pexels.com/photos/196644/pexels-photo-196644.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=1200",
                "Master the principles of great mobile UI/UX design — typography, color, spacing, and interaction.",
                540, "English", "Beginner", 4.7, 8420, 69.99, 24.99,
                List.of("No prior experience needed"),
                List.of("Design beautiful mobile apps", "Understand design systems", "Prototype in Figma"),
                List.of(Map.of("q", "Is Figma required?", "a", "Optional — the free tier is plenty.")),
                true, now),
            course("crs-python-data", "Python for Data Science", "Dr. Priya Rao",
                "PhD in Statistics, teaching data science for 8 years.",
                "cat-data", "Data Science",
                "https://images.pexels.com/photos/1181671/pexels-photo-1181671.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=800",
                "https://images.pexels.com/photos/1181671/pexels-photo-1181671.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=1200",
                "From zero to data hero. Learn pandas, numpy, matplotlib and build real machine-learning projects.",
                900, "English", "Beginner", 4.9, 21030, 99.99, 44.99,
                List.of("Basic Python is a plus"),
                List.of("Analyze data with pandas", "Build ML models", "Visualize insights"),
                List.of(), true, now),
            course("crs-digital-marketing", "Digital Marketing 2026", "Alex Rivera",
                "Growth marketer for high-growth SaaS companies.",
                "cat-mkt", "Marketing",
                "https://images.pexels.com/photos/265087/pexels-photo-265087.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=800",
                "https://images.pexels.com/photos/265087/pexels-photo-265087.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=1200",
                "Master SEO, paid ads, content marketing, and analytics.",
                480, "English", "Intermediate", 4.6, 5210, 79.99, 29.99,
                List.of("None"), List.of("Rank on Google", "Run profitable ads", "Grow audiences"),
                List.of(), true, now),
            course("crs-spanish", "Spanish for Beginners", "Isabella Martínez",
                "Certified language teacher from Madrid.",
                "cat-lang", "Languages",
                "https://images.pexels.com/photos/207658/pexels-photo-207658.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=800",
                "https://images.pexels.com/photos/207658/pexels-photo-207658.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=1200",
                "Learn conversational Spanish in 30 days.",
                360, "English", "Beginner", 4.8, 9840, 59.99, 19.99,
                List.of("Curiosity"),
                List.of("Hold basic conversations", "Understand grammar", "Travel with confidence"),
                List.of(), true, now),
            course("crs-startup", "Startup Fundamentals", "Jordan Kim",
                "Founder of two successful startups.",
                "cat-biz", "Business",
                "https://images.pexels.com/photos/3184291/pexels-photo-3184291.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=800",
                "https://images.pexels.com/photos/3184291/pexels-photo-3184291.jpeg?auto=compress&cs=tinysrgb&dpr=2&w=1200",
                "From idea to funded startup — the practical playbook.",
                600, "English", "Advanced", 4.7, 3120, 129.99, 49.99,
                List.of("Business curiosity"),
                List.of("Validate ideas", "Pitch investors", "Build MVPs"),
                List.of(), true, now)
        );
        courseRepo.saveAll(courses);

        // Lectures (5 per course)
        List<Lecture> lectures = new ArrayList<>();
        for (Course c : courses) {
            for (int i = 1; i <= 5; i++) {
                Lecture l = new Lecture();
                l.setId("lec-" + c.getId() + "-" + i);
                l.setCourseId(c.getId());
                l.setTitle("Lesson " + i + ": " + c.getTitle().split(" ")[0] + " - Part " + i);
                l.setType(i % 2 == 1 ? "video" : "pdf");
                l.setUrl(i % 2 == 1 ? sampleVideo : samplePdf);
                l.setDurationSeconds(596);
                l.setOrder(i);
                l.setDescription("In this lesson we cover key concepts of " + c.getTitle() + ".");
                l.setNotes("Take notes and try the exercises at the end.");
                lectures.add(l);
            }
        }
        lectureRepo.saveAll(lectures);

        // Quizzes
        Quiz quizRN = new Quiz();
        quizRN.setId("quiz-react-native-1");
        quizRN.setCourseId("crs-react-native");
        quizRN.setTitle("React Native Basics");
        quizRN.setDurationMinutes(10);
        quizRN.setQuestions(List.of(
            q("q1", "mcq", "Which company created React Native?",
                List.of("Google", "Meta", "Microsoft", "Apple"), List.of("Meta")),
            q("q2", "true_false", "Expo Router uses file-based routing.",
                List.of("True", "False"), List.of("True")),
            q("q3", "multi", "Which are core React Native components? (select all)",
                List.of("View", "div", "Text", "span"), List.of("View", "Text")),
            q("q4", "mcq", "What is the entry file with Expo Router?",
                List.of("index.js", "App.tsx", "app/_layout.tsx", "expo-router/entry"),
                List.of("expo-router/entry")),
            q("q5", "fill", "Which hook navigates imperatively? use___()",
                List.of(), List.of("router"))
        ));

        Quiz quizDesign = new Quiz();
        quizDesign.setId("quiz-ui-design-1");
        quizDesign.setCourseId("crs-ui-design");
        quizDesign.setTitle("Design Foundations");
        quizDesign.setDurationMinutes(8);
        quizDesign.setQuestions(List.of(
            q("q1", "mcq", "Which is a design system?",
                List.of("Material Design", "MP3", "TCP/IP", "HTML"), List.of("Material Design")),
            q("q2", "true_false", "8pt grid is a common spacing system.",
                List.of("True", "False"), List.of("True")),
            q("q3", "mcq", "Minimum touch target on iOS?",
                List.of("24px", "32px", "44px", "60px"), List.of("44px"))
        ));
        quizRepo.saveAll(List.of(quizRN, quizDesign));

        // Notifications
        List<Notification> notifs = List.of(
            notif("Welcome to Lumina Learn!", "Discover thousands of courses to level up your skills.",
                "announcement", now),
            notif("New Course: React Native Masterclass",
                "Sarah Chen just dropped a brand new course. Check it out!",
                "course_update", now.minus(1, ChronoUnit.DAYS)),
            notif("Weekend Sale: 60% Off",
                "Grab your favorite courses at up to 60% off this weekend only.",
                "announcement", now.minus(2, ChronoUnit.DAYS))
        );
        notifRepo.saveAll(notifs);

        seedLiveClasses();
        log.info("Seed complete.");
    }

    private void seedLiveClasses() {
        if (liveClassRepo.count() > 0) return;
        Instant now = Instant.now();
        List<LiveClass> live = List.of(
            liveClass("live-rn-1", "crs-react-native", "Live Q&A: React Native + Expo Router",
                "Ask anything about Expo Router and native modules.",
                "https://meet.google.com/lookup/lumina-rn-live",
                now.plus(6, ChronoUnit.HOURS), now.plus(7, ChronoUnit.HOURS),
                60, "Sarah Chen", null),
            liveClass("live-ui-1", "crs-ui-design", "Design Critique Session",
                "Bring your Figma files — we'll review them live.",
                "https://meet.google.com/lookup/lumina-ui-live",
                now.plus(26, ChronoUnit.HOURS), now.plus(27, ChronoUnit.HOURS),
                60, "Marcus Lee", null),
            liveClass("live-py-1", "crs-python-data", "Pandas Workshop (Recording)",
                "Recorded live workshop — watch anytime.",
                "https://meet.google.com/lookup/lumina-py-live",
                now.minus(48, ChronoUnit.HOURS), now.minus(47, ChronoUnit.HOURS),
                60, "Dr. Priya Rao",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
        );
        liveClassRepo.saveAll(live);
        log.info("Live classes seeded.");
    }

    // ---- Factory helpers ----
    private Category cat(String id, String name, String icon) {
        Category c = new Category();
        c.setId(id); c.setName(name); c.setIcon(icon);
        return c;
    }

    private Course course(String id, String title, String instructor, String bio,
                          String catId, String catName, String thumb, String banner,
                          String desc, int durMin, String lang, String level,
                          double rating, int students, double price, double discountPrice,
                          List<String> reqs, List<String> outcomes,
                          List<Map<String, Object>> faqs, boolean cert, Instant now) {
        Course c = new Course();
        c.setId(id); c.setTitle(title); c.setInstructor(instructor); c.setInstructorBio(bio);
        c.setCategoryId(catId); c.setCategory(catName); c.setThumbnail(thumb); c.setBanner(banner);
        c.setDescription(desc); c.setDurationMinutes(durMin); c.setLanguage(lang); c.setLevel(level);
        c.setRating(rating); c.setStudents(students); c.setPrice(price); c.setDiscountPrice(discountPrice);
        c.setRequirements(reqs); c.setOutcomes(outcomes); c.setFaqs(faqs); c.setCertificate(cert);
        c.setCreatedAt(now.toString());
        return c;
    }

    private Map<String, Object> q(String id, String type, String question,
                                   List<String> options, List<String> correct) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("type", type); m.put("question", question);
        m.put("options", options); m.put("correct", correct);
        return m;
    }

    private Notification notif(String title, String body, String type, Instant at) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID().toString());
        n.setTitle(title); n.setBody(body); n.setType(type);
        n.setBroadcast(true); n.setCreatedAt(at.toString());
        return n;
    }

    private LiveClass liveClass(String id, String courseId, String title, String desc,
                                 String meetUrl, Instant start, Instant end,
                                 int durMin, String instructor, String recordingUrl) {
        LiveClass lc = new LiveClass();
        lc.setId(id); lc.setCourseId(courseId); lc.setTitle(title); lc.setDescription(desc);
        lc.setMeetUrl(meetUrl); lc.setStartTime(start.toString()); lc.setEndTime(end.toString());
        lc.setDurationMinutes(durMin); lc.setInstructor(instructor); lc.setRecordingUrl(recordingUrl);
        return lc;
    }
}
