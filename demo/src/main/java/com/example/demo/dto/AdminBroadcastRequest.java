package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public class AdminBroadcastRequest {
    @NotBlank private String title;
    @NotBlank private String body;
    private String type = "announcement";
    private String userId; // null = broadcast to all

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
