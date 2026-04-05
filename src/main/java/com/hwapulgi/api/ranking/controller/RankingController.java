package com.hwapulgi.api.ranking.controller;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.service.AuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.ranking.dto.MyRankingResponse;
import com.hwapulgi.api.ranking.dto.RankingEntryResponse;
import com.hwapulgi.api.ranking.service.RankingService;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;
    private final AuthService authService;
    private final UserService userService;

    @GetMapping("/points")
    public ApiResponse<List<RankingEntryResponse>> getPointsRanking(
            @RequestParam(defaultValue = "weekly") String period,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(rankingService.getTopRanking("points", period, limit));
    }

    @GetMapping("/release-rate")
    public ApiResponse<List<RankingEntryResponse>> getReleaseRateRanking(
            @RequestParam(defaultValue = "weekly") String period,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(rankingService.getTopRanking("release", period, limit));
    }

    @GetMapping("/me")
    public ApiResponse<MyRankingResponse> getMyRanking(
            @RequestHeader(value = "Authorization", defaultValue = "") String token,
            @RequestParam(defaultValue = "points") String criteria,
            @RequestParam(defaultValue = "weekly") String period) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(rankingService.getMyRanking(user.getId(), criteria, period));
    }
}
