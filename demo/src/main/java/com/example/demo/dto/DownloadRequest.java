package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public class DownloadRequest {
    @NotBlank private String lectureId;
    @NotBlank private String courseId;
    private long sizeBytes;
    private boolean encrypted = true;

    public String getLectureId() { return lectureId; }
    public void setLectureId(String lectureId) { this.lectureId = lectureId; }
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }
}
