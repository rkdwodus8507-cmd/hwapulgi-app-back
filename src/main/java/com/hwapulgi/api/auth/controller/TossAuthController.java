package com.hwapulgi.api.auth.controller;

import com.hwapulgi.api.auth.dto.TossLoginRequest;
import com.hwapulgi.api.auth.dto.TossLoginResponse;
import com.hwapulgi.api.auth.dto.TossRefreshTokenRequest;
import com.hwapulgi.api.auth.dto.TossUnlinkCallbackRequest;
import com.hwapulgi.api.auth.service.TossAuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// TODO: Client ID 발급 후 추가 작업
//  - [ ] Client ID 발급 (사업자 등록 필요 여부 확인)
//  - [ ] AES 복호화 키 수령 후 개인정보 복호화 구현
//  - [ ] 프론트 appLogin() SDK 호출 연동
@RestController
@RequestMapping("/api/auth/toss")
@RequiredArgsConstructor
public class TossAuthController {

    private final TossAuthService tossAuthService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TossLoginResponse>> login(
            @Valid @RequestBody TossLoginRequest request) {
        TossLoginResponse response = tossAuthService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TossLoginResponse>> refresh(
            @Valid @RequestBody TossRefreshTokenRequest request) {
        TossLoginResponse response = tossAuthService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/unlink")
    public ResponseEntity<ApiResponse<Void>> unlink(
            @RequestHeader("Authorization") String authorization) {
        String accessToken = authorization.replace("Bearer ", "");
        tossAuthService.unlink(accessToken);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/callback/unlink")
    public ResponseEntity<Void> unlinkCallback(
            @RequestBody TossUnlinkCallbackRequest request) {
        tossAuthService.handleUnlinkCallback(request.userKey(), request.referrer());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/callback/unlink")
    public ResponseEntity<Void> unlinkCallbackGet(
            @RequestParam Long userKey,
            @RequestParam String referrer) {
        tossAuthService.handleUnlinkCallback(userKey, referrer);
        return ResponseEntity.ok().build();
    }
}
