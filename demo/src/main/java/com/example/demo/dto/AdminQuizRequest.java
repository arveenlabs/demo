package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public class AdminQuizRequest {
    @NotBlank private String courseId;
    @NotBlank private String title;
    private int durationMinutes = 10;
    private List<Map<String, Object>> questions;

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public List<Map<String, Object>> getQuestions() { return questions; }
    public void setQuestions(List<Map<String, Object>> questions) { this.questions = questions; }
}
