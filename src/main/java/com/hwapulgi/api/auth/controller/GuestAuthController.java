package com.hwapulgi.api.auth.controller;

import com.hwapulgi.api.auth.dto.GuestLoginRequest;
import com.hwapulgi.api.auth.dto.GuestLoginResponse;
import com.hwapulgi.api.auth.service.GuestAuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/guest")
@RequiredArgsConstructor
public class GuestAuthController {

    private final GuestAuthService guestAuthService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<GuestLoginResponse>> login(
            @Valid @RequestBody GuestLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(guestAuthService.login(request.deviceId())));
    }
}
