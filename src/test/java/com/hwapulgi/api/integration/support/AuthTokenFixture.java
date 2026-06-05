package com.hwapulgi.api.integration.support;

import com.hwapulgi.api.auth.jwt.JwtTokenProvider;
import com.hwapulgi.api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

/**
 * 통합 테스트에서 사용자에 대한 Bearer 헤더 값을 생성한다.
 * 기존 평문 토큰 "1:테스트유저"을 대체한다.
 */
@TestComponent
@RequiredArgsConstructor
public class AuthTokenFixture {

    private final JwtTokenProvider tokenProvider;

    public String bearerForUser(User user) {
        return "Bearer " + tokenProvider.createAccessToken(user.getId(), user.getNickname());
    }
}
