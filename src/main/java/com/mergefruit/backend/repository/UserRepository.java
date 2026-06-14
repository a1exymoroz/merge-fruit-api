package com.mergefruit.backend.repository;

import com.mergefruit.backend.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/*
 Learning Notes

 What: Spring Data JPA repository — generates SQL from method names.
 Why: Avoids hand-written JDBC; JPA uses parameterized queries (SQL injection safe).

 Try yourself:
 - Add boolean existsByEmailIgnoreCase(String email) for case-insensitive checks.

 Common mistake:
 - Using native queries with string concatenation instead of named parameters.
*/
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
