package com.hwapulgi.api.user.service;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import com.hwapulgi.api.user.dto.UserProfileResponse;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User getOrCreateUser(Long externalUserId, String nickname) {
        String externalId = String.valueOf(externalUserId);
        return userRepository.findByExternalId(externalId)
                .orElseGet(() -> userRepository.save(new User(externalId, nickname)));
    }

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    public UserProfileResponse getProfile(Long userId) {
        User user = findById(userId);
        return UserProfileResponse.from(user);
    }
}
