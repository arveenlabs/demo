package com.example.demo.repository;

import com.example.demo.model.Progress;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface ProgressRepository extends MongoRepository<Progress, String> {
    Optional<Progress> findByUserIdAndLectureId(String userId, String lectureId);
    List<Progress> findByUserId(String userId, Sort sort);
    List<Progress> findByUserIdAndCourseId(String userId, String courseId);
    long countByUserIdAndCourseIdAndCompleted(String userId, String courseId, boolean completed);
    long countByUserIdAndUpdatedAtStartingWith(String userId, String prefix);
}
