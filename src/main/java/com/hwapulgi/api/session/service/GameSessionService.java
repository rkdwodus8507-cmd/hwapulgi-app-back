package com.hwapulgi.api.session.service;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import com.hwapulgi.api.session.dto.GameSessionCreateRequest;
import com.hwapulgi.api.session.dto.GameSessionResponse;
import com.hwapulgi.api.session.entity.GameSession;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import com.hwapulgi.api.user.dto.UserStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameSessionService {

    private final GameSessionRepository gameSessionRepository;
    private final UserService userService;

    @Transactional
    public GameSessionResponse createSession(Long userId, GameSessionCreateRequest request) {
        User user = userService.findById(userId);

        int calculatedPoints = PointsCalculator.calculate(
                request.getHits(), request.getSkillShots(),
                request.getAngerBefore(), request.getAngerAfter());

        if (calculatedPoints != request.getPoints()) {
            throw new BusinessException(ErrorCode.POINTS_MISMATCH);
        }

        GameSession session = GameSession.builder()
                .user(user)
                .target(request.getTarget())
                .customTarget(request.getCustomTarget())
                .targetNickname(request.getTargetNickname())
                .angerBefore(request.getAngerBefore())
                .angerAfter(request.getAngerAfter())
                .hits(request.getHits())
                .skillShots(request.getSkillShots())
                .releasedPercent(request.getReleasedPercent())
                .points(calculatedPoints)
                .memo(request.getMemo())
                .build();

        return GameSessionResponse.from(gameSessionRepository.save(session));
    }

    public Page<GameSessionResponse> getMySessions(Long userId, Pageable pageable) {
        return gameSessionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(GameSessionResponse::from);
    }

    public GameSessionResponse getSession(UUID sessionId) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        return GameSessionResponse.from(session);
    }

    @Transactional
    public GameSessionResponse updateAngerAfter(UUID sessionId, int angerAfter) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        session.updateAngerAfter(angerAfter);
        return GameSessionResponse.from(session);
    }

    public UserStatsResponse getUserStats(Long userId, LocalDateTime from) {
        List<GameSession> sessions = gameSessionRepository.findByUserIdAndCreatedAtAfter(userId, from);
        if (sessions.isEmpty()) {
            return new UserStatsResponse(0, 0, 0, 0, 0);
        }
        int totalSessions = sessions.size();
        int totalHits = sessions.stream().mapToInt(GameSession::getHits).sum();
        int totalPoints = sessions.stream().mapToInt(GameSession::getPoints).sum();
        int bestRelease = sessions.stream().mapToInt(GameSession::getReleasedPercent).max().orElse(0);
        int avgRelease = (int) sessions.stream().mapToInt(GameSession::getReleasedPercent).average().orElse(0);
        return new UserStatsResponse(totalSessions, totalHits, totalPoints, bestRelease, avgRelease);
    }
}
