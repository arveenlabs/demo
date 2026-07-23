package com.example.demo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notification_reads")
public class NotificationRead {
    @Id private String id;
    private String userId;
    private String notificationId;
    private String readAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }
    public String getReadAt() { return readAt; }
    public void setReadAt(String readAt) { this.readAt = readAt; }
}
