package ca.pharmaforecast.backend.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findTop5ByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<Notification> findTop30ByOrganizationIdOrderBySentAtDescCreatedAtDesc(UUID organizationId);

    List<Notification> findTop30ByOrganizationIdAndReadAtIsNullOrderBySentAtDescCreatedAtDesc(UUID organizationId);

    @Modifying
    @Query("update Notification n set n.readAt = current_timestamp where n.organizationId = :organizationId and n.readAt is null")
    int markAllRead(UUID organizationId);

    @Query(value = """
            select exists (
                select 1
                from notifications n
                where n.organization_id = :organizationId
                  and n.location_id = :locationId
                  and n.type = :#{#type.name()}
                  and n.payload ->> 'din' = :din
                  and n.sent_at >= :start
                  and n.sent_at < :end
            )
            """, nativeQuery = true)
    boolean existsSameDayDinNotification(
            UUID organizationId,
            UUID locationId,
            NotificationType type,
            String din,
            Instant start,
            Instant end
    );

}
