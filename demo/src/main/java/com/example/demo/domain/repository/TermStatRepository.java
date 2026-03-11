package com.example.demo.domain.repository;

import com.example.demo.domain.entity.TermStat;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermStatRepository extends JpaRepository<TermStat, Long> {
    List<TermStat> findByTermIdIn(Collection<Long> termIds);
}
