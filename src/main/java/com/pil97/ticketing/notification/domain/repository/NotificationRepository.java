package com.pil97.ticketing.notification.domain.repository;

import com.pil97.ticketing.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
