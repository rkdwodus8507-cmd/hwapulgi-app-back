package com.hwapulgi.api.auth.service;

import com.hwapulgi.api.auth.dto.GuestLoginResponse;
import com.hwapulgi.api.auth.jwt.JwtTokenProvider;
import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestAuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;

    @Value("${jwt.access-token-expiry-seconds}")
    private long accessExpiry;

    @Transactional
    public GuestLoginResponse login(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        User user = userRepository.findByDeviceId(deviceId)
                .orElseGet(() -> {
                    User saved = userRepository.save(User.guest(deviceId, "temp"));
                    saved.updateNickname("게스트" + saved.getId());
                    return saved;
                });
        return new GuestLoginResponse(
                user.getId(),
                user.getNickname(),
                tokenProvider.createAccessToken(user.getId(), user.getNickname()),
                tokenProvider.createRefreshToken(user.getId()),
                accessExpiry
        );
    }
}
