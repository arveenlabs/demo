package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public class AdminLiveRequest {
    @NotBlank private String courseId;
    @NotBlank private String title;
    private String description = "";
    @NotBlank private String meetUrl;
    @NotBlank private String startTime;
    @NotBlank private String endTime;
    private int durationMinutes = 60;
    @NotBlank private String instructor;
    private String recordingUrl;

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMeetUrl() { return meetUrl; }
    public void setMeetUrl(String meetUrl) { this.meetUrl = meetUrl; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getInstructor() { return instructor; }
    public void setInstructor(String instructor) { this.instructor = instructor; }
    public String getRecordingUrl() { return recordingUrl; }
    public void setRecordingUrl(String recordingUrl) { this.recordingUrl = recordingUrl; }
}
