package com.regulyn.dsar.repository;

import com.regulyn.dsar.entity.DsarRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DsarRequestRepository extends JpaRepository<DsarRequestEntity, UUID> {
}
