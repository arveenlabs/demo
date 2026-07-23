package com.example.demo.repository;

import com.example.demo.model.WishlistItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends MongoRepository<WishlistItem, String> {
    Optional<WishlistItem> findByUserIdAndCourseId(String userId, String courseId);
    List<WishlistItem> findByUserId(String userId);
    void deleteByUserIdAndCourseId(String userId, String courseId);
}
