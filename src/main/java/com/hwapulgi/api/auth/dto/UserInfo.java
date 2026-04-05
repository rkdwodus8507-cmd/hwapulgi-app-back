package com.hwapulgi.api.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserInfo {
    private final Long userId;
    private final String nickname;
}
