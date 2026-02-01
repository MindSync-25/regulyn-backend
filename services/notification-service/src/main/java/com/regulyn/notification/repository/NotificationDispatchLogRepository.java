package com.regulyn.notification.repository;

import com.regulyn.notification.entity.NotificationDispatchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationDispatchLogRepository extends JpaRepository<NotificationDispatchLog, UUID> {
}
