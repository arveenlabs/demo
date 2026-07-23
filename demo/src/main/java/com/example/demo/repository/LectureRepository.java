package com.example.demo.repository;

import com.example.demo.model.Lecture;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface LectureRepository extends MongoRepository<Lecture, String> {
    List<Lecture> findByCourseIdOrderByOrderAsc(String courseId);
    long countByCourseId(String courseId);
}
