package com.ustb.seforge.identity.repository;

import com.ustb.seforge.identity.domain.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ustb.seforge.identity.domain.AccountType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select u from User u join UserProfile p on p.userId = u.id "
            + "where (:accountType is null or p.accountType = :accountType) "
            + "and (:search is null or lower(u.username) like :search "
            + "or lower(u.email) like :search or lower(p.displayName) like :search "
            + "or lower(p.studentNo) like :search)")
    Page<User> search(@Param("accountType") AccountType accountType,
                      @Param("search") String search, Pageable pageable);
}
