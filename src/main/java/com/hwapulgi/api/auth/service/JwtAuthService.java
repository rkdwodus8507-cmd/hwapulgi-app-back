package com.hwapulgi.api.auth.service;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.jwt.JwtPayload;
import com.hwapulgi.api.auth.jwt.JwtTokenProvider;
import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Primary
@Profile({"local", "dev", "prod"})
@RequiredArgsConstructor
public class JwtAuthService implements AuthService {
    private final JwtTokenProvider tokenProvider;

    @Override
    public UserInfo authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        JwtPayload payload = tokenProvider.parseAccessToken(token);
        return new UserInfo(payload.userId(), payload.nickname());
    }
}
