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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void findById_returnsUser_whenPresent() {
        User existing = User.tossUser("1", "기존유저");
        given(userRepository.findById(1L)).willReturn(Optional.of(existing));

        User result = userService.findById(1L);

        assertThat(result.getNickname()).isEqualTo("기존유저");
    }

    @Test
    void findById_throws_whenNotFound() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(999L))
                .isInstanceOf(BusinessException.class);
    }
}
