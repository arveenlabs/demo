package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateOrderRequest {
    @NotBlank private String courseId;

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
}
