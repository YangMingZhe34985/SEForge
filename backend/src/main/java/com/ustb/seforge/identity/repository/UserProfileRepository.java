package com.ustb.seforge.identity.repository;

import com.ustb.seforge.identity.domain.UserProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByUserId(Long userId);

    Optional<UserProfile> findByStudentNoIgnoreCase(String studentNo);

    boolean existsByStudentNoIgnoreCase(String studentNo);

    long countByAccountType(com.ustb.seforge.identity.domain.AccountType accountType);

    List<UserProfile> findAllByUserIdIn(Collection<Long> userIds);
}
