package com.svivanrilski.svirerp.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserLoginHistoryRepository extends JpaRepository<UserLoginHistory, UUID> {
    List<UserLoginHistory> findTop50ByOrderByLoginAtDesc();
    List<UserLoginHistory> findByEmailIgnoreCaseOrderByLoginAtDesc(String email);
}
