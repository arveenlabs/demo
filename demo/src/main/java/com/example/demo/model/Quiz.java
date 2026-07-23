package com.example.demo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Document(collection = "quizzes")
public class Quiz {
    @Id private String id;
    private String courseId;
    private String title;
    private int durationMinutes;
    private List<Map<String, Object>> questions;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public List<Map<String, Object>> getQuestions() { return questions; }
    public void setQuestions(List<Map<String, Object>> questions) { this.questions = questions; }
}
