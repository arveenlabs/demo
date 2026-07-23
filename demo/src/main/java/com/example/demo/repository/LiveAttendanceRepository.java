package com.example.demo.repository;

import com.example.demo.model.LiveAttendance;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface LiveAttendanceRepository extends MongoRepository<LiveAttendance, String> {
    Optional<LiveAttendance> findByUserIdAndLiveClassId(String userId, String liveClassId);
}
