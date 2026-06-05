package com.hwapulgi.api.user.repository;

import com.hwapulgi.api.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class UserRepositoryDeviceIdTest {

    @Autowired
    UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void findByDeviceId_returns_user_when_device_matches() {
        userRepository.save(User.guest("device-uuid-1", "게스트"));
        assertThat(userRepository.findByDeviceId("device-uuid-1")).isPresent();
    }

    @Test
    void findByDeviceId_returns_empty_when_not_found() {
        assertThat(userRepository.findByDeviceId("nope")).isEmpty();
    }
}
