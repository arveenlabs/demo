package com.example.demo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "lectures")
public class Lecture {
    @Id private String id;
    private String courseId;
    private String title;
    private String type;
    private String url;
    private int durationSeconds;
    private int order;
    private String description;
    private String notes;

    // Transient fields (not stored, populated at query time)
    private int watchedSeconds;
    private boolean completed;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public int getWatchedSeconds() { return watchedSeconds; }
    public void setWatchedSeconds(int watchedSeconds) { this.watchedSeconds = watchedSeconds; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
