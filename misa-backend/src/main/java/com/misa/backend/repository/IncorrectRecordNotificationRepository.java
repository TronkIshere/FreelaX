package com.misa.backend.repository;

import com.misa.backend.entity.IncorrectRecordNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IncorrectRecordNotificationRepository extends JpaRepository<IncorrectRecordNotification, UUID> {
}
