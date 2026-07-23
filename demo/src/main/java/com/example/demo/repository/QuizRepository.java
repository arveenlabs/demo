package com.example.demo.repository;

import com.example.demo.model.Quiz;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface QuizRepository extends MongoRepository<Quiz, String> {
    List<Quiz> findByCourseId(String courseId);
}
