package com.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ManagerRepository extends JpaRepository<Manager,Long> {
    java.util.Optional<Manager> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByPhoneNumber(Long phoneNumber);

}
