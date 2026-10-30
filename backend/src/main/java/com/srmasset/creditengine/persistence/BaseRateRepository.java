package com.srmasset.creditengine.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BaseRateRepository extends JpaRepository<BaseRateEntity, UUID> {

	List<BaseRateEntity> findByValidFromLessThanEqual(OffsetDateTime validFrom);

}
