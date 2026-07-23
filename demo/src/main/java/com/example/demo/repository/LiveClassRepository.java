package com.example.demo.repository;

import com.example.demo.model.LiveClass;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface LiveClassRepository extends MongoRepository<LiveClass, String> {
    @Query("{ 'courseId': { '$in': ?0 }, 'endTime': { '$gte': ?1 } }")
    List<LiveClass> findUpcomingForEnrolled(List<String> courseIds, String nowIso, Sort sort);

    @Query("{ 'courseId': { '$in': ?0 } }")
    List<LiveClass> findAllForEnrolled(List<String> courseIds, Sort sort);

    @Query("{ 'courseId': { '$in': ?0 }, 'endTime': { '$gte': ?1 } }")
    List<LiveClass> findUpcomingByCourseIdIn(List<String> courseIds, String nowIso, Sort sort);
}
