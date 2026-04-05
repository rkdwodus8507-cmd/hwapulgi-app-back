package com.hwapulgi.api.session.controller;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.service.AuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.session.dto.AngerAfterUpdateRequest;
import com.hwapulgi.api.session.dto.GameSessionCreateRequest;
import com.hwapulgi.api.session.dto.GameSessionResponse;
import com.hwapulgi.api.session.service.GameSessionService;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Session", description = "게임 세션 관리")
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class GameSessionController {

    private final GameSessionService gameSessionService;
    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "게임 세션 저장", description = "게임 결과를 저장하고 포인트를 검증합니다. 랭킹/업적도 자동 갱신됩니다.")
    @PostMapping
    public ApiResponse<GameSessionResponse> createSession(
            @RequestHeader(value = "Authorization", defaultValue = "") String token,
            @Valid @RequestBody GameSessionCreateRequest request) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(gameSessionService.createSession(user.getId(), request));
    }

    @Operation(summary = "내 세션 목록", description = "내 게임 세션 목록을 페이징 조회합니다.")
    @GetMapping
    public ApiResponse<Page<GameSessionResponse>> getMySessions(
            @RequestHeader(value = "Authorization", defaultValue = "") String token,
            @PageableDefault(size = 20) Pageable pageable) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(gameSessionService.getMySessions(user.getId(), pageable));
    }

    @Operation(summary = "세션 상세 조회", description = "본인 세션만 조회 가능합니다.")
    @GetMapping("/{id}")
    public ApiResponse<GameSessionResponse> getSession(
            @RequestHeader(value = "Authorization", defaultValue = "") String token,
            @PathVariable UUID id) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(gameSessionService.getSession(id, user.getId()));
    }

    @Operation(summary = "분노 수치 조정", description = "게임 후 분노 수치를 수정합니다. 포인트는 변경되지 않습니다.")
    @PatchMapping("/{id}/anger-after")
    public ApiResponse<GameSessionResponse> updateAngerAfter(
            @RequestHeader(value = "Authorization", defaultValue = "") String token,
            @PathVariable UUID id,
            @Valid @RequestBody AngerAfterUpdateRequest request) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(gameSessionService.updateAngerAfter(id, user.getId(), request.getAngerAfter()));
    }
}
