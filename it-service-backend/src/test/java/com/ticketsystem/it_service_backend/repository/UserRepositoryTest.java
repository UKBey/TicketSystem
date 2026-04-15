package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringJUnitConfig(RepositoryTestConfig.class)
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        userRepository.save(User.builder().id("u1").email("a1@example.com").fullName("Agent One").role("AGENT").build());
        userRepository.save(User.builder().id("u2").email("a2@example.com").fullName("Agent Two").role("AGENT").build());
        userRepository.save(User.builder().id("u3").email("c1@example.com").fullName("Customer One").role("CUSTOMER").build());
    }

    @Test
    void findByRole_returnsOnlyMatchingRole() {
        List<User> agents = userRepository.findByRole("AGENT");

        assertEquals(2, agents.size());
    }
}
