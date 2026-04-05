package com.hwapulgi.api.achievement.repository;

import com.hwapulgi.api.achievement.entity.Achievement;
import com.hwapulgi.api.achievement.entity.AchievementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AchievementRepository extends JpaRepository<Achievement, Long> {
    List<Achievement> findByUserIdOrderByAchievedAtDesc(Long userId);
    boolean existsByUserIdAndAchievementType(Long userId, AchievementType type);
}
