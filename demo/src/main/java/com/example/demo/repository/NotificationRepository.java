package com.example.demo.repository;

import com.example.demo.model.Notification;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    @Query("{ '$or': [ { 'userId': ?0 }, { 'broadcast': true } ] }")
    List<Notification> findForUser(String userId, Sort sort);
}
