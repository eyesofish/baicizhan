package com.example.demo.domain.repository;

import com.example.demo.domain.entity.Sense;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SenseRepository extends JpaRepository<Sense, Long> {
    List<Sense> findByTermIdOrderByIdAsc(Long termId);
}
