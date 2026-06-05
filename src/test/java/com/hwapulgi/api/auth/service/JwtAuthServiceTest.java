package com.hwapulgi.api.auth.service;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.jwt.JwtTokenProvider;
import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;

class JwtAuthServiceTest {
    private final JwtTokenProvider provider = new JwtTokenProvider(
        "0123456789abcdef0123456789abcdef", 3600L, 86_400L * 30, Clock.systemUTC()
    );
    private final JwtAuthService service = new JwtAuthService(provider);

    @Test
    void authenticate_returns_userInfo_when_bearer_token_valid() {
        String token = provider.createAccessToken(7L, "닉");
        UserInfo info = service.authenticate("Bearer " + token);
        assertThat(info.userId()).isEqualTo(7L);
        assertThat(info.nickname()).isEqualTo("닉");
    }

    @Test
    void authenticate_throws_when_header_missing_bearer_prefix() {
        String token = provider.createAccessToken(7L, "닉");
        assertThatThrownBy(() -> service.authenticate(token))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    void authenticate_throws_when_token_blank() {
        assertThatThrownBy(() -> service.authenticate(""))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.authenticate("Bearer "))
            .isInstanceOf(BusinessException.class);
    }
}
