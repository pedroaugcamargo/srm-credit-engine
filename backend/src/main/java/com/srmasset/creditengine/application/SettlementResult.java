package com.srmasset.creditengine.application;

import com.srmasset.creditengine.domain.settlement.SettlementSnapshot;

public record SettlementResult(SettlementSnapshot snapshot, boolean replayed) {
}
