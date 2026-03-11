package com.example.demo.domain.repository;

import com.example.demo.domain.entity.AiJob;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiJobRepository extends JpaRepository<AiJob, Long> {
    Optional<AiJob> findByIdAndUserId(Long id, Long userId);

    Optional<AiJob> findByOpenaiResponseId(String openaiResponseId);
}
