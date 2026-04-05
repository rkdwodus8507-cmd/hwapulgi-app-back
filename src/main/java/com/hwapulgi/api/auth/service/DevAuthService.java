package com.hwapulgi.api.auth.service;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"local", "dev"})
public class DevAuthService implements AuthService {

    @Override
    public UserInfo authenticate(String token) {
        if (token == null || token.isBlank()) {
            return new UserInfo(1L, "테스트유저");
        }
        try {
            String[] parts = token.split(":");
            Long userId = Long.parseLong(parts[0]);
            String nickname = parts.length > 1 ? parts[1] : "유저" + userId;
            return new UserInfo(userId, nickname);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
