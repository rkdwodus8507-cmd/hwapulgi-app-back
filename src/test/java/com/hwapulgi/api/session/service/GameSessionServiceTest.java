package com.hwapulgi.api.session.service;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.ranking.service.RankingService;
import com.hwapulgi.api.session.dto.GameSessionCreateRequest;
import com.hwapulgi.api.session.dto.GameSessionResponse;
import com.hwapulgi.api.session.entity.GameSession;
import com.hwapulgi.api.session.repository.GameSessionRepository;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GameSessionServiceTest {

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private UserService userService;

    @Mock
    private RankingService rankingService;

    @InjectMocks
    private GameSessionService gameSessionService;

    @Test
    void createSession_validPoints_succeeds() {
        User user = new User("1", "테스트");
        given(userService.findById(1L)).willReturn(user);
        given(gameSessionRepository.save(any(GameSession.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // points = 10 + 50 + (5*4) + (80-30)/2 = 105
        GameSessionCreateRequest request = new GameSessionCreateRequest(
                "회사", null, "상사닉네임", 80, 30, 50, 5, 62, 105, null);

        GameSessionResponse response = gameSessionService.createSession(1L, request);

        assertThat(response.getPoints()).isEqualTo(105);
        assertThat(response.getTarget()).isEqualTo("회사");
    }

    @Test
    void createSession_invalidPoints_throwsException() {
        User user = new User("1", "테스트");
        given(userService.findById(1L)).willReturn(user);

        GameSessionCreateRequest request = new GameSessionCreateRequest(
                "회사", null, "상사닉네임", 80, 30, 50, 5, 62, 9999, null);

        assertThatThrownBy(() -> gameSessionService.createSession(1L, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getSession_notFound_throwsException() {
        UUID id = UUID.randomUUID();
        given(gameSessionRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> gameSessionService.getSession(id))
                .isInstanceOf(BusinessException.class);
    }
}
