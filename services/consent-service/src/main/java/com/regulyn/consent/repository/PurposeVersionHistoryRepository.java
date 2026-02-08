package com.regulyn.consent.repository;

import com.regulyn.consent.entity.PurposeVersionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PurposeVersionHistoryRepository extends JpaRepository<PurposeVersionHistory, UUID> {
}
