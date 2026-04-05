package com.hwapulgi.api.home.controller;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.service.AuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.home.dto.HomeSnapshotResponse;
import com.hwapulgi.api.home.service.HomeService;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Home", description = "홈 화면")
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "홈 스냅샷 조회", description = "오늘 세션 수, 최근 대상, 이번 주 통계 등 홈 화면 대시보드 데이터")
    @GetMapping("/snapshot")
    public ApiResponse<HomeSnapshotResponse> getSnapshot(
            @RequestHeader(value = "Authorization", defaultValue = "") String token) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(homeService.getSnapshot(user.getId()));
    }
}
