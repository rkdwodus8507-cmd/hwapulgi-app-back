package com.hwapulgi.api.auth.jwt;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {
    private final JwtTokenProvider provider = new JwtTokenProvider(
        "0123456789abcdef0123456789abcdef", 3600L, 86_400L * 30, Clock.systemUTC()
    );

    @Test
    void createAccessToken_then_parse_returns_userId_and_nickname() {
        String token = provider.createAccessToken(42L, "테스트유저");
        JwtPayload payload = provider.parseAccessToken(token);
        assertThat(payload.userId()).isEqualTo(42L);
        assertThat(payload.nickname()).isEqualTo("테스트유저");
    }

    @Test
    void parseAccessToken_throws_when_signature_invalid() {
        JwtTokenProvider other = new JwtTokenProvider(
            "different-secret-different-secret", 3600L, 86_400L * 30, Clock.systemUTC()
        );
        String tampered = other.createAccessToken(42L, "x");
        assertThatThrownBy(() -> provider.parseAccessToken(tampered))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    void parseAccessToken_throws_when_expired() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        JwtTokenProvider shortLived = new JwtTokenProvider(
            "0123456789abcdef0123456789abcdef", 1L, 1L, fixed
        );
        String token = shortLived.createAccessToken(1L, "x");
        Clock later = Clock.fixed(Instant.parse("2026-01-01T00:00:10Z"), ZoneOffset.UTC);
        JwtTokenProvider laterProvider = new JwtTokenProvider(
            "0123456789abcdef0123456789abcdef", 1L, 1L, later
        );
        assertThatThrownBy(() -> laterProvider.parseAccessToken(token))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    void refreshToken_does_not_contain_nickname_claim() {
        String refresh = provider.createRefreshToken(42L);
        JwtRefreshPayload payload = provider.parseRefreshToken(refresh);
        assertThat(payload.userId()).isEqualTo(42L);
    }

    @Test
    void parseAccessToken_throws_when_given_a_refresh_token() {
        String refresh = provider.createRefreshToken(1L);
        assertThatThrownBy(() -> provider.parseAccessToken(refresh))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    void parseRefreshToken_throws_when_given_an_access_token() {
        String access = provider.createAccessToken(1L, "x");
        assertThatThrownBy(() -> provider.parseRefreshToken(access))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }
}
