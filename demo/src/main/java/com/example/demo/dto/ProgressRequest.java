package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public class ProgressRequest {
    @NotBlank private String courseId;
    @NotBlank private String lectureId;
    private int watchedSeconds;
    private boolean completed;

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }
    public int getWatchedSeconds() { return watchedSeconds; }
    public void setWatchedSeconds(int watchedSeconds) { this.watchedSeconds = watchedSeconds; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
