package com.example.demo.domain.repository;

import com.example.demo.domain.entity.ExampleSentence;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExampleSentenceRepository extends JpaRepository<ExampleSentence, Long> {
    List<ExampleSentence> findBySenseIdIn(Collection<Long> senseIds);
}
