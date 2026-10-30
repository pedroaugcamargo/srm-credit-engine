package com.srmasset.creditengine.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<SettlementEntity, UUID> {
}
