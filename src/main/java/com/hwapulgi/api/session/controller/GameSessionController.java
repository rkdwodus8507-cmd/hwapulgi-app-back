package com.hwapulgi.api.session.controller;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.service.AuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.ranking.service.RankingService;
import com.hwapulgi.api.session.dto.AngerAfterUpdateRequest;
import com.hwapulgi.api.session.dto.GameSessionCreateRequest;
import com.hwapulgi.api.session.dto.GameSessionResponse;
import com.hwapulgi.api.session.service.GameSessionService;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class GameSessionController {

    private final GameSessionService gameSessionService;
    private final RankingService rankingService;
    private final AuthService authService;
    private final UserService userService;

    @PostMapping
    public ApiResponse<GameSessionResponse> createSession(
            @RequestHeader(value = "Authorization", defaultValue = "") String token,
            @Valid @RequestBody GameSessionCreateRequest request) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());

        GameSessionResponse response = gameSessionService.createSession(user.getId(), request);

        rankingService.addPoints(user.getId(), response.getPoints());
        rankingService.updateReleaseRate(user.getId(), response.getReleasedPercent());

        return ApiResponse.ok(response);
    }

    @GetMapping
    public ApiResponse<Page<GameSessionResponse>> getMySessions(
            @RequestHeader(value = "Authorization", defaultValue = "") String token,
            @PageableDefault(size = 20) Pageable pageable) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(gameSessionService.getMySessions(user.getId(), pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<GameSessionResponse> getSession(@PathVariable UUID id) {
        return ApiResponse.ok(gameSessionService.getSession(id));
    }

    @PatchMapping("/{id}/anger-after")
    public ApiResponse<GameSessionResponse> updateAngerAfter(
            @PathVariable UUID id,
            @Valid @RequestBody AngerAfterUpdateRequest request) {
        return ApiResponse.ok(gameSessionService.updateAngerAfter(id, request.getAngerAfter()));
    }
}
