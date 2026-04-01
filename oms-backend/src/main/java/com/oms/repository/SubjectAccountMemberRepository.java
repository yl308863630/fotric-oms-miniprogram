package com.oms.repository;

import com.oms.entity.SubjectAccountMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubjectAccountMemberRepository extends JpaRepository<SubjectAccountMember, Long> {
    Optional<SubjectAccountMember> findByUsername(String username);
    List<SubjectAccountMember> findByGroupId(Long groupId);
    void deleteByUsername(String username);
}
