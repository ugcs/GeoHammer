package com.ugcs.geohammer.geotagger;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressTest {

	private record Update(int progress, int maxProgress) {

		int percent() {
			return (int) ((long) progress * 100 / maxProgress);
		}
	}

	@Test
	void largeRunReportsOnlyPercentageChanges() {
		int maxProgress = 1_000_000;
		List<Update> updates = new ArrayList<>();
		Progress progress = new Progress(maxProgress, (p, max) -> updates.add(new Update(p, max)));

		for (int i = 0; i < maxProgress; i++) {
			progress.increment();
		}

		assertEquals(101, updates.size());
		assertEquals(0, updates.getFirst().percent());
		assertEquals(new Update(maxProgress, maxProgress), updates.getLast());
		for (int i = 1; i < updates.size(); i++) {
			assertTrue(updates.get(i).percent() > updates.get(i - 1).percent());
		}
	}

	@Test
	void smallRunReportsEveryIncrement() {
		List<Update> updates = new ArrayList<>();
		Progress progress = new Progress(3, (p, max) -> updates.add(new Update(p, max)));

		for (int i = 0; i < 3; i++) {
			progress.increment();
		}

		assertEquals(List.of(new Update(1, 3), new Update(2, 3), new Update(3, 3)), updates);
	}

	@Test
	void incrementsBeyondMaxReportNothing() {
		List<Update> updates = new ArrayList<>();
		Progress progress = new Progress(2, (p, max) -> updates.add(new Update(p, max)));

		for (int i = 0; i < 5; i++) {
			progress.increment();
		}

		assertEquals(List.of(new Update(1, 2), new Update(2, 2)), updates);
	}

	@Test
	void largeMaxProgressDoesNotOverflow() {
		int maxProgress = Integer.MAX_VALUE / 100 + 1_000_000;
		List<Update> updates = new ArrayList<>();
		Progress progress = new Progress(maxProgress, (p, max) -> updates.add(new Update(p, max)));

		for (int i = 0; i < maxProgress; i++) {
			progress.increment();
		}

		assertEquals(101, updates.size());
		for (int i = 0; i < updates.size(); i++) {
			assertEquals(i, updates.get(i).percent());
		}
	}
}
