package com.revpay;

import com.revpay.model.User;
import com.revpay.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testFindByEmail_NotFound() {
        Optional<User> userOpt = userRepository.findByEmail("nonexistent_user@example.com");
        assertFalse(userOpt.isPresent(), "User should not be found matching the fake email");
    }

    @Test
    public void testFindByPhone_NotFound() {
        Optional<User> userOpt = userRepository.findByPhone("0000000000");
        assertFalse(userOpt.isPresent(), "User should not be found matching the fake phone");
    }
}
