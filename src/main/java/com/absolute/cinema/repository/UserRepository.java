package com.absolute.cinema.repository;

import com.absolute.cinema.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(@Param("email") String email);
    Boolean existsByEmail(@Param("email") String email);
}
