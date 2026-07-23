package com.example.demo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Document(collection = "courses")
public class Course {
    @Id private String id;
    private String title;
    private String instructor;
    private String instructorBio;
    private String categoryId;
    private String category;
    private String thumbnail;
    private String banner;
    private String description;
    private int durationMinutes;
    private String language;
    private String level;
    private double rating;
    private int students;
    private double price;
    private Double discountPrice;
    private List<String> requirements;
    private List<String> outcomes;
    private List<Map<String, Object>> faqs;
    private boolean certificate;
    private String createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getInstructor() { return instructor; }
    public void setInstructor(String instructor) { this.instructor = instructor; }
    public String getInstructorBio() { return instructorBio; }
    public void setInstructorBio(String instructorBio) { this.instructorBio = instructorBio; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }
    public String getBanner() { return banner; }
    public void setBanner(String banner) { this.banner = banner; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public int getStudents() { return students; }
    public void setStudents(int students) { this.students = students; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public Double getDiscountPrice() { return discountPrice; }
    public void setDiscountPrice(Double discountPrice) { this.discountPrice = discountPrice; }
    public List<String> getRequirements() { return requirements; }
    public void setRequirements(List<String> requirements) { this.requirements = requirements; }
    public List<String> getOutcomes() { return outcomes; }
    public void setOutcomes(List<String> outcomes) { this.outcomes = outcomes; }
    public List<Map<String, Object>> getFaqs() { return faqs; }
    public void setFaqs(List<Map<String, Object>> faqs) { this.faqs = faqs; }
    public boolean isCertificate() { return certificate; }
    public void setCertificate(boolean certificate) { this.certificate = certificate; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
