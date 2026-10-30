package com.srmasset.creditengine.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FxRateRepository extends JpaRepository<FxRateEntity, UUID> {

	List<FxRateEntity> findByBaseCurrencyAndQuoteCurrencyAndValidFromLessThanEqual(String baseCurrency, String quoteCurrency, OffsetDateTime validFrom);

}
