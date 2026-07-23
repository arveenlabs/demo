package com.example.demo.repository;

import com.example.demo.model.NotificationRead;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface NotificationReadRepository extends MongoRepository<NotificationRead, String> {
    List<NotificationRead> findByUserId(String userId);
    Optional<NotificationRead> findByUserIdAndNotificationId(String userId, String notificationId);
}
