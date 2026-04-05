package com.hwapulgi.api.user.dto;

import com.hwapulgi.api.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UserProfileResponse {
    private final Long id;
    private final String nickname;
    private final LocalDateTime createdAt;

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getId(), user.getNickname(), user.getCreatedAt());
    }
}
