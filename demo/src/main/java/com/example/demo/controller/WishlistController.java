package com.example.demo.controller;

import com.example.demo.model.Course;
import com.example.demo.model.WishlistItem;
import com.example.demo.repository.CourseRepository;
import com.example.demo.repository.WishlistRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistRepository wishlistRepo;
    private final MongoTemplate mongoTemplate;

    public WishlistController(WishlistRepository wishlistRepo, CourseRepository courseRepo,
                              MongoTemplate mongoTemplate) {
        this.wishlistRepo = wishlistRepo;
        this.mongoTemplate = mongoTemplate;
    }

    @PostMapping("/{courseId}")
    public Map<String, String> add(@PathVariable String courseId, Authentication auth) {
        String userId = auth.getName();
        if (wishlistRepo.findByUserIdAndCourseId(userId, courseId).isPresent()) {
            return Map.of("message", "Already in wishlist");
        }
        WishlistItem item = new WishlistItem();
        item.setId(UUID.randomUUID().toString());
        item.setUserId(userId);
        item.setCourseId(courseId);
        item.setCreatedAt(Instant.now().toString());
        wishlistRepo.save(item);
        return Map.of("message", "Added to wishlist");
    }

    @DeleteMapping("/{courseId}")
    public Map<String, String> remove(@PathVariable String courseId, Authentication auth) {
        wishlistRepo.deleteByUserIdAndCourseId(auth.getName(), courseId);
        return Map.of("message", "Removed from wishlist");
    }

    @GetMapping
    public List<Course> getWishlist(Authentication auth) {
        List<WishlistItem> items = wishlistRepo.findByUserId(auth.getName());
        if (items.isEmpty()) return List.of();
        List<String> ids = items.stream().map(WishlistItem::getCourseId).toList();
        return mongoTemplate.find(new Query(Criteria.where("_id").in(ids)), Course.class);
    }
}
