package com.regulyn.consent.repository;

import com.regulyn.consent.entity.ConsentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ConsentStatusHistoryRepository extends JpaRepository<ConsentStatusHistory, UUID> {
}
