package com.example.demo.domain.repository;

import com.example.demo.domain.entity.VocabList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VocabListRepository extends JpaRepository<VocabList, Long> {
    List<VocabList> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<VocabList> findByIdAndUserId(Long id, Long userId);
}
