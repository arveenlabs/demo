package com.example.demo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "enrollments")
public class Enrollment {
    @Id private String id;
    private String userId;
    private String courseId;
    private String enrolledAt;
    private boolean paid;
    private String orderId;
    private long amount;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public String getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(String enrolledAt) { this.enrolledAt = enrolledAt; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
}
