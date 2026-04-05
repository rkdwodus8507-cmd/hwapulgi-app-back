package com.hwapulgi.api.auth.service;

import com.hwapulgi.api.auth.dto.UserInfo;

public interface AuthService {
    UserInfo authenticate(String token);
}
