package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public class AdminLectureRequest {
    @NotBlank private String courseId;
    @NotBlank private String title;
    @NotBlank private String type;
    @NotBlank private String url;
    private int durationSeconds = 0;
    private int order = 1;
    private String description = "";
    private String notes = "";

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
}
