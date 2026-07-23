package com.example.demo.repository;

import com.example.demo.model.Enrollment;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends MongoRepository<Enrollment, String> {
    Optional<Enrollment> findByUserIdAndCourseId(String userId, String courseId);
    List<Enrollment> findByUserId(String userId);
    long countByUserId(String userId);
    void deleteByCourseId(String courseId);
}
