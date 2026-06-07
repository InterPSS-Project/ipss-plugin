package org.interpss.threePhase.qsts.control;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class QstsControlQueue {
	private final PriorityQueue<QueuedAction> actions = new PriorityQueue<QueuedAction>();
	private final Map<String, Long> latestSequenceByKey = new HashMap<String, Long>();
	private long nextSequence;

	public void schedule(QstsControlAction action) {
		if(action == null) {
			return;
		}
		String key = action.getKey();
		if(key == null || key.trim().isEmpty()) {
			throw new IllegalArgumentException("QSTS control action requires a stable key");
		}
		long sequence = ++nextSequence;
		latestSequenceByKey.put(key, Long.valueOf(sequence));
		actions.add(new QueuedAction(action, sequence));
	}

	public int processUntil(double timeSeconds) {
		int applied = 0;
		while(!actions.isEmpty() && actions.peek().action.getExecuteTimeSeconds() <= timeSeconds) {
			QueuedAction queued = actions.poll();
			Long latestSequence = latestSequenceByKey.get(queued.action.getKey());
			if(latestSequence == null || latestSequence.longValue() != queued.sequence) {
				continue;
			}
			latestSequenceByKey.remove(queued.action.getKey());
			if(queued.action.apply()) {
				applied++;
			}
		}
		return applied;
	}

	public void cancel(String key) {
		if(key != null) {
			latestSequenceByKey.remove(key);
		}
	}

	public void clear() {
		actions.clear();
		latestSequenceByKey.clear();
	}

	public boolean isEmpty() {
		return latestSequenceByKey.isEmpty();
	}

	public int size() {
		return latestSequenceByKey.size();
	}

	private static class QueuedAction implements Comparable<QueuedAction> {
		private final QstsControlAction action;
		private final long sequence;

		private QueuedAction(QstsControlAction action, long sequence) {
			this.action = action;
			this.sequence = sequence;
		}

		@Override
		public int compareTo(QueuedAction other) {
			int timeCompare = Double.compare(action.getExecuteTimeSeconds(), other.action.getExecuteTimeSeconds());
			return timeCompare != 0 ? timeCompare : Long.compare(sequence, other.sequence);
		}
	}
}
