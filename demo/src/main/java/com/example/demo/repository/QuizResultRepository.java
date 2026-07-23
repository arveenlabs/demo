package com.example.demo.repository;

import com.example.demo.model.QuizResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface QuizResultRepository extends MongoRepository<QuizResult, String> {
    List<QuizResult> findByUserIdOrderBySubmittedAtDesc(String userId);
    long countByUserId(String userId);
}
