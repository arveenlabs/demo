package com.example.demo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Document(collection = "quiz_results")
public class QuizResult {
    @Id private String id;
    private String userId;
    private String quizId;
    private String courseId;
    private int score;
    private int correct;
    private int total;
    private int timeTakenSeconds;
    private String submittedAt;
    private List<Map<String, Object>> review;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getQuizId() { return quizId; }
    public void setQuizId(String quizId) { this.quizId = quizId; }
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public int getCorrect() { return correct; }
    public void setCorrect(int correct) { this.correct = correct; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getTimeTakenSeconds() { return timeTakenSeconds; }
    public void setTimeTakenSeconds(int timeTakenSeconds) { this.timeTakenSeconds = timeTakenSeconds; }
    public String getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }
    public List<Map<String, Object>> getReview() { return review; }
    public void setReview(List<Map<String, Object>> review) { this.review = review; }
}
