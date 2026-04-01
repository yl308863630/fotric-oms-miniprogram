package com.oms.repository;

import com.oms.entity.SubjectAccountGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubjectAccountGroupRepository extends JpaRepository<SubjectAccountGroup, Long> {
    Optional<SubjectAccountGroup> findByGroupKey(String groupKey);
}
