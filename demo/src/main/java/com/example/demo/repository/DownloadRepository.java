package com.example.demo.repository;

import com.example.demo.model.Download;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface DownloadRepository extends MongoRepository<Download, String> {
    Optional<Download> findByUserIdAndLectureId(String userId, String lectureId);
    List<Download> findByUserId(String userId, Sort sort);
    void deleteByUserIdAndLectureId(String userId, String lectureId);
}
