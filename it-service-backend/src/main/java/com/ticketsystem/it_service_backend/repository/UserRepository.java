package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserRepository extends JpaRepository<User, String> {
    List<User> findByRole(String role);
}