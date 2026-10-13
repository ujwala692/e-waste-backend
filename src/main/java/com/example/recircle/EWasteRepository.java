package com.example.recircle;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EWasteRepository extends JpaRepository<EWaste,Long> {
    List<EWaste> findByUserIdOrderByCreatedAtDesc(Long userId);
    void deleteByUserId(Long userId);
}
