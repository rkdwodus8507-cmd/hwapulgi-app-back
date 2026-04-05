package com.hwapulgi.api.user.controller;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.service.AuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.session.dto.TargetStatsResponse;
import com.hwapulgi.api.session.service.GameSessionService;
import com.hwapulgi.api.user.dto.UserProfileResponse;
import com.hwapulgi.api.user.dto.UserStatsResponse;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final GameSessionService gameSessionService;
    private final AuthService authService;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile(
            @RequestHeader(value = "Authorization", defaultValue = "") String token) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(userService.getProfile(user.getId()));
    }

    @GetMapping("/me/stats")
    public ApiResponse<UserStatsResponse> getMyStats(
            @RequestHeader(value = "Authorization", defaultValue = "") String token,
            @RequestParam(defaultValue = "weekly") String period) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());

        LocalDateTime from = switch (period) {
            case "monthly" -> LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
            default -> LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        };

        return ApiResponse.ok(gameSessionService.getUserStats(user.getId(), from));
    }

    @GetMapping("/me/target-stats")
    public ApiResponse<List<TargetStatsResponse>> getMyTargetStats(
            @RequestHeader(value = "Authorization", defaultValue = "") String token) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(gameSessionService.getTargetStats(user.getId()));
    }
}
