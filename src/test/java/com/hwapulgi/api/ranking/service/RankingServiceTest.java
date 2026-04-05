package com.hwapulgi.api.ranking.service;

import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RankingServiceTest {

    private RankingService rankingService;
    private StringRedisTemplate redisTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection redisConnection;

    @BeforeEach
    void setUp() {
        given(connectionFactory.getConnection()).willReturn(redisConnection);
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        rankingService = new RankingService(redisTemplate, userRepository);
    }

    @Test
    void currentWeekKey_returnsCorrectFormat() {
        String key = RankingService.currentWeekKey();
        assertThat(key).matches("\\d{4}-W\\d{2}");
    }

    @Test
    void currentMonthKey_returnsCorrectFormat() {
        String key = RankingService.currentMonthKey();
        assertThat(key).matches("\\d{4}-\\d{2}");
    }
}
