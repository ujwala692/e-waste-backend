package com.example.recircle;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CollectionRepository extends JpaRepository<CollectionRequest,Long> {
    List<CollectionRequest> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countByStatus(CollectionStatus status);
    void deleteByUserId(Long userId);
}
