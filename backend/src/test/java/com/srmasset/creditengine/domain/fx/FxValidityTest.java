package com.srmasset.creditengine.domain.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FxValidityTest {

	private static final Instant AT = OffsetDateTime.parse("2026-10-29T12:00:00-03:00").toInstant();

	private final FxValidity validity = new FxValidity(Duration.ofHours(24));

	@Test
	void selectsTheLatestRateAtOrBeforeTheInstant() {
		FxQuote older = quote("5.0000", "2026-10-27T12:00:00-03:00");
		FxQuote current = quote("5.4321", "2026-10-29T10:00:00-03:00");
		FxQuote future = quote("5.9999", "2026-10-29T13:00:00-03:00");

		FxSelection selection = validity.select(List.of(future, older, current), AT);

		assertEquals(current.id(), selection.quote().id());
		assertEquals(0, new BigDecimal("5.4321").compareTo(selection.quote().rate()));
		assertFalse(selection.stale());
	}

	@Test
	void keepsARateAgedExactly24HoursFresh() {
		FxQuote quote = quote("5.4321", "2026-10-28T12:00:00-03:00");

		FxSelection selection = validity.select(List.of(quote), AT);

		assertEquals(0, new BigDecimal("5.4321").compareTo(selection.quote().rate()));
		assertFalse(selection.stale());
	}

	@Test
	void marksARateOlderThan24HoursAsStale() {
		FxQuote quote = quote("5.0000", "2026-10-28T11:59:59-03:00");

		FxSelection selection = validity.select(List.of(quote), AT);

		assertEquals(0, new BigDecimal("5.0000").compareTo(selection.quote().rate()));
		assertTrue(selection.stale());
	}

	@Test
	void usesTheConfiguredMaxAge() {
		FxValidity oneHour = new FxValidity(Duration.ofHours(1));
		FxQuote quote = quote("5.4321", "2026-10-29T10:00:00-03:00");

		FxSelection selection = oneHour.select(List.of(quote), AT);

		assertTrue(selection.stale());
	}

	@Test
	void rejectsWhenNoRateIsValidAtTheInstant() {
		FxQuote future = quote("5.9999", "2026-10-29T13:00:00-03:00");

		assertThrows(FxRateUnavailableException.class, () -> validity.select(List.of(future), AT));
	}

	private static FxQuote quote(String rate, String validFrom) {
		return new FxQuote(
				UUID.randomUUID(),
				"USD",
				"BRL",
				new BigDecimal(rate),
				OffsetDateTime.parse(validFrom));
	}

}
