package com.hwapulgi.api.streak.controller;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.service.AuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.streak.dto.StreakResponse;
import com.hwapulgi.api.streak.service.StreakService;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Streak", description = "연속 플레이")
@RestController
@RequestMapping("/api/v1/streaks")
@RequiredArgsConstructor
public class StreakController {

    private final StreakService streakService;
    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "내 스트릭 조회", description = "현재 연속일, 최고 연속일, 총 플레이 일수")
    @GetMapping("/me")
    public ApiResponse<StreakResponse> getMyStreak(
            @RequestHeader(value = "Authorization", defaultValue = "") String token) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(streakService.getStreak(user.getId()));
    }
}
