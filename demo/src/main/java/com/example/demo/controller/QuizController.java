package com.example.demo.controller;

import com.example.demo.dto.QuizSubmitRequest;
import com.example.demo.model.Quiz;
import com.example.demo.model.QuizResult;
import com.example.demo.repository.QuizRepository;
import com.example.demo.repository.QuizResultRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
public class QuizController {

    private final QuizRepository quizRepo;
    private final QuizResultRepository resultRepo;

    public QuizController(QuizRepository quizRepo, QuizResultRepository resultRepo) {
        this.quizRepo = quizRepo;
        this.resultRepo = resultRepo;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sanitizeQuestions(List<Map<String, Object>> questions) {
        List<Map<String, Object>> safe = new ArrayList<>();
        for (Map<String, Object> q : questions) {
            Map<String, Object> sq = new LinkedHashMap<>(q);
            sq.remove("correct");
            safe.add(sq);
        }
        return safe;
    }

    @GetMapping("/courses/{courseId}/quizzes")
    public List<Map<String, Object>> courseQuizzes(@PathVariable String courseId, Authentication auth) {
        List<Quiz> quizzes = quizRepo.findByCourseId(courseId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Quiz q : quizzes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.getId());
            m.put("course_id", q.getCourseId());
            m.put("title", q.getTitle());
            m.put("duration_minutes", q.getDurationMinutes());
            m.put("questions", sanitizeQuestions(q.getQuestions() != null ? q.getQuestions() : List.of()));
            result.add(m);
        }
        return result;
    }

    @GetMapping("/quizzes/{quizId}")
    public Map<String, Object> getQuiz(@PathVariable String quizId, Authentication auth) {
        Quiz quiz = quizRepo.findById(quizId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", quiz.getId());
        m.put("course_id", quiz.getCourseId());
        m.put("title", quiz.getTitle());
        m.put("duration_minutes", quiz.getDurationMinutes());
        m.put("questions", sanitizeQuestions(quiz.getQuestions() != null ? quiz.getQuestions() : List.of()));
        return m;
    }

    @PostMapping("/quizzes/submit")
    @SuppressWarnings("unchecked")
    public Map<String, Object> submitQuiz(@Valid @RequestBody QuizSubmitRequest body, Authentication auth) {
        Quiz quiz = quizRepo.findById(body.getQuizId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found"));

        // Build answer map: questionId -> List<String>
        Map<String, List<String>> ansMap = new HashMap<>();
        if (body.getAnswers() != null) {
            for (QuizSubmitRequest.QuizAnswerItem a : body.getAnswers()) {
                ansMap.put(a.getQuestionId(), a.getAnswer() != null ? a.getAnswer() : List.of());
            }
        }

        int correct = 0;
        List<Map<String, Object>> review = new ArrayList<>();
        List<Map<String, Object>> questions = quiz.getQuestions() != null ? quiz.getQuestions() : List.of();

        for (Map<String, Object> q : questions) {
            String qId = (String) q.get("id");
            List<String> userAns = ansMap.getOrDefault(qId, List.of());
            List<String> correctAns = (List<String>) q.getOrDefault("correct", List.of());

            List<String> userSorted = userAns.stream()
                    .map(s -> s.toLowerCase().strip()).sorted().toList();
            List<String> correctSorted = correctAns.stream()
                    .map(s -> s.toLowerCase().strip()).sorted().toList();

            boolean isCorrect = userSorted.equals(correctSorted);
            if (isCorrect) correct++;

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("question_id", qId);
            r.put("question", q.get("question"));
            r.put("user_answer", userAns);
            r.put("correct_answer", correctAns);
            r.put("is_correct", isCorrect);
            review.add(r);
        }

        int total = questions.size();
        int score = total > 0 ? (int) Math.round((correct * 100.0) / total) : 0;

        QuizResult qr = new QuizResult();
        qr.setId(UUID.randomUUID().toString());
        qr.setUserId(auth.getName());
        qr.setQuizId(body.getQuizId());
        qr.setCourseId(quiz.getCourseId());
        qr.setScore(score);
        qr.setCorrect(correct);
        qr.setTotal(total);
        qr.setTimeTakenSeconds(body.getTimeTakenSeconds());
        qr.setSubmittedAt(Instant.now().toString());
        qr.setReview(review);
        resultRepo.save(qr);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", qr.getId());
        out.put("user_id", qr.getUserId());
        out.put("quiz_id", qr.getQuizId());
        out.put("course_id", qr.getCourseId());
        out.put("score", score);
        out.put("correct", correct);
        out.put("total", total);
        out.put("time_taken_seconds", body.getTimeTakenSeconds());
        out.put("submitted_at", qr.getSubmittedAt());
        out.put("review", review);
        return out;
    }

    @GetMapping("/my/quiz-results")
    public List<QuizResult> myQuizResults(Authentication auth) {
        return resultRepo.findByUserIdOrderBySubmittedAtDesc(auth.getName());
    }
}
