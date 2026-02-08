package com.regulyn.incident.repository;

import com.regulyn.incident.entity.NoticeTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

public interface NoticeTemplateRepository extends JpaRepository<NoticeTemplateEntity, UUID> {
	Optional<NoticeTemplateEntity> findByTenantIdAndTemplateTypeAndName(UUID tenantId, String templateType, String name);

	@Lock(PESSIMISTIC_WRITE)
	Optional<NoticeTemplateEntity> findById(UUID id);
}
