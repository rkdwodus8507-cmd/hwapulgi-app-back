package com.hwapulgi.api.session.repository;

import com.hwapulgi.api.session.entity.GameSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {
    Page<GameSession> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT gs FROM GameSession gs WHERE gs.user.id = :userId AND gs.createdAt >= :from")
    List<GameSession> findByUserIdAndCreatedAtAfter(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    @Query("SELECT DISTINCT CAST(gs.createdAt AS LocalDate) FROM GameSession gs WHERE gs.user.id = :userId ORDER BY CAST(gs.createdAt AS LocalDate) DESC")
    List<java.time.LocalDate> findDistinctPlayDatesByUserId(@Param("userId") Long userId);

    @Query("SELECT new com.hwapulgi.api.session.dto.TargetStatsResponse(" +
           "gs.target, COUNT(gs), SUM(gs.hits), AVG(gs.releasedPercent)) " +
           "FROM GameSession gs WHERE gs.user.id = :userId " +
           "GROUP BY gs.target ORDER BY COUNT(gs) DESC")
    List<com.hwapulgi.api.session.dto.TargetStatsResponse> findTargetStatsByUserId(@Param("userId") Long userId);
}
