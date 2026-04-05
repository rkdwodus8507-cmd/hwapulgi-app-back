package com.hwapulgi.api.achievement.controller;

import com.hwapulgi.api.achievement.dto.AchievementResponse;
import com.hwapulgi.api.achievement.service.AchievementService;
import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.service.AuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Achievement", description = "업적/배지")
@RestController
@RequestMapping("/api/v1/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;
    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "내 업적 목록")
    @GetMapping("/me")
    public ApiResponse<List<AchievementResponse>> getMyAchievements(
            @RequestHeader(value = "Authorization", defaultValue = "") String token) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(achievementService.getMyAchievements(user.getId()));
    }
}
