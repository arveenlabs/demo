package com.example.demo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "live_attendance")
public class LiveAttendance {
    @Id private String id;
    private String userId;
    private String liveClassId;
    private String courseId;
    private String joinedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getLiveClassId() { return liveClassId; }
    public void setLiveClassId(String liveClassId) { this.liveClassId = liveClassId; }
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public String getJoinedAt() { return joinedAt; }
    public void setJoinedAt(String joinedAt) { this.joinedAt = joinedAt; }
}
