package com.clinic.backend.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Single notification with user/patient eagerly fetched (for DTO mapping, OSIV off). */
    @Query("""
        SELECT n FROM Notification n
        LEFT JOIN FETCH n.user
        LEFT JOIN FETCH n.patient
        WHERE n.id = :id
        """)
    Optional<Notification> findWithRefsById(@Param("id") Long id);

    /**
     * The drain query: rows still EN_ATTENTE whose schedule has come due
     * (immediate when scheduled_at is null). Oldest first, capped by the caller.
     */
    @Query("""
        SELECT n FROM Notification n
        WHERE n.status = 'EN_ATTENTE'
          AND (n.scheduledAt IS NULL OR n.scheduledAt <= :now)
        ORDER BY n.createdAt ASC
        """)
    List<Notification> findDue(@Param("now") LocalDateTime now);

    /**
     * Filtered list for the REST/admin view. Any param may be null to skip.
     * Most recent first.
     */
    @Query("""
        SELECT n FROM Notification n
        LEFT JOIN FETCH n.user
        LEFT JOIN FETCH n.patient
        WHERE (:userId IS NULL OR n.user.id = :userId)
          AND (:status IS NULL OR :status = '' OR n.status = :status)
          AND (:type   IS NULL OR :type   = '' OR n.type   = :type)
        ORDER BY n.createdAt DESC
        """)
    List<Notification> search(@Param("userId") Long userId,
                              @Param("status") String status,
                              @Param("type") String type);

    /** In-app inbox for a staff member: their IN_APP notifications, most recent first. */
    @Query("""
        SELECT n FROM Notification n
        LEFT JOIN FETCH n.patient
        WHERE n.user.id = :userId AND n.channel = 'IN_APP'
        ORDER BY n.createdAt DESC
        """)
    List<Notification> findInboxForUser(@Param("userId") Long userId);

    /** Unread in-app count for the nav badge. */
    @Query("SELECT COUNT(n) FROM Notification n " +
           "WHERE n.user.id = :userId AND n.channel = 'IN_APP' AND n.readAt IS NULL")
    long countUnreadForUser(@Param("userId") Long userId);
}
