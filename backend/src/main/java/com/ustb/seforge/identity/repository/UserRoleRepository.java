package com.ustb.seforge.identity.repository;

import com.ustb.seforge.identity.domain.UserRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    @Query(value = "select r.code from user_roles ur join roles r on r.id = ur.role_id where ur.user_id = :userId",
            nativeQuery = true)
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    void deleteAllByUserId(Long userId);

    @Query(value = "select count(*) from user_roles ur join roles r on r.id = ur.role_id "
            + "where ur.user_id = :userId and r.code = :roleCode", nativeQuery = true)
    long countByUserIdAndRoleCode(@Param("userId") Long userId, @Param("roleCode") String roleCode);

    default boolean existsByUserIdAndRoleCode(Long userId, String roleCode) {
        return countByUserIdAndRoleCode(userId, roleCode) > 0;
    }

    @Query(value = "select count(*) from user_roles ur join roles r on r.id = ur.role_id "
            + "where r.code = :roleCode", nativeQuery = true)
    long countAnyByRoleCode(@Param("roleCode") String roleCode);

    default boolean existsAnyByRoleCode(String roleCode) {
        return countAnyByRoleCode(roleCode) > 0;
    }
}
