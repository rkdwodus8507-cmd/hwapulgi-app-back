package com.hwapulgi.api.user.service;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void getOrCreateUser_existingUser_returnsUser() {
        User existing = new User("1", "기존유저");
        given(userRepository.findByExternalId("1")).willReturn(Optional.of(existing));

        User result = userService.getOrCreateUser(1L, "기존유저");

        assertThat(result.getNickname()).isEqualTo("기존유저");
    }

    @Test
    void getOrCreateUser_newUser_createsAndReturns() {
        given(userRepository.findByExternalId("2")).willReturn(Optional.empty());
        User newUser = new User("2", "새유저");
        given(userRepository.saveAndFlush(any(User.class))).willReturn(newUser);

        User result = userService.getOrCreateUser(2L, "새유저");

        assertThat(result.getNickname()).isEqualTo("새유저");
    }

    @Test
    void findById_notFound_throwsBusinessException() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(999L))
                .isInstanceOf(BusinessException.class);
    }
}
