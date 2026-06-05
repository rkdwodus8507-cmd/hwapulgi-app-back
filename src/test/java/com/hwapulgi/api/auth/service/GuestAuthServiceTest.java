package com.hwapulgi.api.auth.service;

import com.hwapulgi.api.auth.dto.GuestLoginResponse;
import com.hwapulgi.api.auth.jwt.JwtTokenProvider;
import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
class GuestAuthServiceTest {

    @Autowired GuestAuthService service;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenProvider tokenProvider;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void login_creates_new_user_when_deviceId_unseen() {
        GuestLoginResponse res = service.login("uuid-new");
        assertThat(userRepository.findByDeviceId("uuid-new")).isPresent();
        assertThat(res.nickname()).isEqualTo("게스트" + res.userId());
        assertThat(tokenProvider.parseAccessToken(res.accessToken()).userId())
                .isEqualTo(res.userId());
    }

    @Test
    void login_returns_existing_user_when_deviceId_known() {
        GuestLoginResponse first = service.login("uuid-known");
        GuestLoginResponse second = service.login("uuid-known");
        assertThat(second.userId()).isEqualTo(first.userId());
    }

    @Test
    void login_throws_when_deviceId_blank() {
        assertThatThrownBy(() -> service.login(""))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.login("   "))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.login(null))
                .isInstanceOf(BusinessException.class);
    }
}
