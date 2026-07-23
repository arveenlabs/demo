package com.example.demo.repository;

import com.example.demo.model.Course;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface CourseRepository extends MongoRepository<Course, String> {
    List<Course> findByCategoryId(String categoryId, Sort sort);
    List<Course> findAll(Sort sort);
}
