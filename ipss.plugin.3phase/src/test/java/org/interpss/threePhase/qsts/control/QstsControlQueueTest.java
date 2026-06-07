package org.interpss.threePhase.qsts.control;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class QstsControlQueueTest {
	@Test
	void delayedActionsExecuteAtScheduledTimeAndReplaceStaleActions() {
		List<String> applied = new ArrayList<String>();
		QstsControlQueue queue = new QstsControlQueue();

		queue.schedule(new RecordingAction("cap:cap1", 10.0, applied, "stale-open"));
		queue.schedule(new RecordingAction("cap:cap1", 20.0, applied, "close"));
		queue.schedule(new RecordingAction("cap:cap2", 15.0, applied, "open"));

		assertEquals(2, queue.size());
		assertEquals(0, queue.processUntil(14.999));
		assertEquals(List.of(), applied);

		assertEquals(1, queue.processUntil(15.0));
		assertEquals(List.of("open"), applied);
		assertEquals(1, queue.size());

		assertEquals(1, queue.processUntil(20.0));
		assertEquals(List.of("open", "close"), applied);
		assertEquals(0, queue.size());
	}

	@Test
	void canceledActionDoesNotExecuteWhenDelayExpires() {
		List<String> applied = new ArrayList<String>();
		QstsControlQueue queue = new QstsControlQueue();

		queue.schedule(new RecordingAction("cap:cap1", 10.0, applied, "open"));
		queue.cancel("cap:cap1");

		assertEquals(0, queue.size());
		assertEquals(0, queue.processUntil(10.0));
		assertEquals(List.of(), applied);
	}

	private static class RecordingAction implements QstsControlAction {
		private final String key;
		private final double executeTimeSeconds;
		private final List<String> applied;
		private final String value;

		private RecordingAction(String key, double executeTimeSeconds, List<String> applied, String value) {
			this.key = key;
			this.executeTimeSeconds = executeTimeSeconds;
			this.applied = applied;
			this.value = value;
		}

		@Override
		public String getKey() {
			return key;
		}

		@Override
		public double getExecuteTimeSeconds() {
			return executeTimeSeconds;
		}

		@Override
		public boolean apply() {
			applied.add(value);
			return true;
		}
	}
}
