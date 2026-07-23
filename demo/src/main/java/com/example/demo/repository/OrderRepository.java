package com.example.demo.repository;

import com.example.demo.model.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {
    Optional<Order> findByIdAndUserId(String id, String userId);
    List<Order> findByUserId(String userId, Sort sort);
    List<Order> findByStatus(String status);
    List<Order> findAll(Sort sort);
}
