package com.regulyn.evidence.repository;

import com.regulyn.evidence.entity.EvidenceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EvidenceRecordRepository extends JpaRepository<EvidenceRecord, UUID> {
}
