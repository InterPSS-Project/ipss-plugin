package org.interpss.core.sparse.solver.klusolvex;

import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight opt-in timing counters for the KLUSolveX adapter.
 */
public final class KlusolveXPerformanceCounters {
	public static final String PROFILE_PROPERTY = "ipss.klusolvex.profile";

	private static final KlusolveXPerformanceCounters INSTANCE = new KlusolveXPerformanceCounters();
	private static final boolean ENABLED = Boolean.getBoolean(PROFILE_PROPERTY);

	private final LongAdder handles = new LongAdder();
	private final LongAdder options = new LongAdder();
	private final LongAdder fullBuilds = new LongAdder();
	private final LongAdder reuseBuilds = new LongAdder();
	private final LongAdder reuseFallbacks = new LongAdder();
	private final LongAdder nonzeros = new LongAdder();
	private final LongAdder setCalls = new LongAdder();
	private final LongAdder primitiveCalls = new LongAdder();
	private final LongAdder incrementCalls = new LongAdder();
	private final LongAdder zeroiseCalls = new LongAdder();
	private final LongAdder factorCalls = new LongAdder();
	private final LongAdder solveCalls = new LongAdder();

	private final LongAdder handleNanos = new LongAdder();
	private final LongAdder optionNanos = new LongAdder();
	private final LongAdder matrixTraversalNanos = new LongAdder();
	private final LongAdder nativeMatrixNanos = new LongAdder();
	private final LongAdder factorNanos = new LongAdder();
	private final LongAdder rhsPackNanos = new LongAdder();
	private final LongAdder rhsCollectNanos = new LongAdder();
	private final LongAdder rhsNativeWriteNanos = new LongAdder();
	private final LongAdder nativeSolveNanos = new LongAdder();
	private final LongAdder resultUnpackNanos = new LongAdder();
	private final LongAdder complexResultNanos = new LongAdder();
	private final LongAdder equationCopyNanos = new LongAdder();
	private final LongAdder equationCreateNanos = new LongAdder();
	private final LongAdder equationStoreNanos = new LongAdder();

	static {
		if(ENABLED) {
			Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println(INSTANCE.formatSummary())));
		}
	}

	private KlusolveXPerformanceCounters() {
	}

	static boolean enabled() {
		return ENABLED;
	}

	public static boolean isEnabled() {
		return ENABLED;
	}

	public static String summary() {
		return INSTANCE.formatSummary();
	}

	static KlusolveXPerformanceCounters get() {
		return INSTANCE;
	}

	static long start() {
		return ENABLED ? System.nanoTime() : 0L;
	}

	void addHandle(long nanos) {
		if(!ENABLED) {
			return;
		}
		handles.increment();
		handleNanos.add(nanos);
	}

	void addOption(long nanos) {
		if(!ENABLED) {
			return;
		}
		options.increment();
		optionNanos.add(nanos);
	}

	void addFullBuild(long entries, long traversalNanos) {
		if(!ENABLED) {
			return;
		}
		fullBuilds.increment();
		nonzeros.add(entries);
		matrixTraversalNanos.add(traversalNanos);
	}

	void addReuseBuild(long entries, long traversalNanos) {
		if(!ENABLED) {
			return;
		}
		reuseBuilds.increment();
		nonzeros.add(entries);
		matrixTraversalNanos.add(traversalNanos);
	}

	void addReuseFallback() {
		if(!ENABLED) {
			return;
		}
		reuseFallbacks.increment();
	}

	void addSet(long nanos) {
		if(!ENABLED) {
			return;
		}
		setCalls.increment();
		nativeMatrixNanos.add(nanos);
	}

	void addPrimitive(long nanos) {
		if(!ENABLED) {
			return;
		}
		primitiveCalls.increment();
		nativeMatrixNanos.add(nanos);
	}

	void addIncrement(long nanos) {
		if(!ENABLED) {
			return;
		}
		incrementCalls.increment();
		nativeMatrixNanos.add(nanos);
	}

	void addZeroise(long nanos) {
		if(!ENABLED) {
			return;
		}
		zeroiseCalls.increment();
		nativeMatrixNanos.add(nanos);
	}

	void addFactor(long nanos) {
		if(!ENABLED) {
			return;
		}
		factorCalls.increment();
		factorNanos.add(nanos);
	}

	void addSolve(long rhsPack, long nativeSolve, long resultUnpack) {
		if(!ENABLED) {
			return;
		}
		solveCalls.increment();
		rhsPackNanos.add(rhsPack);
		nativeSolveNanos.add(nativeSolve);
		resultUnpackNanos.add(resultUnpack);
	}

	void addRhsPackBreakdown(long rhsCollect, long rhsNativeWrite) {
		if(!ENABLED) {
			return;
		}
		rhsCollectNanos.add(rhsCollect);
		rhsNativeWriteNanos.add(rhsNativeWrite);
	}

	void addComplexResult(long nanos) {
		if(!ENABLED) {
			return;
		}
		complexResultNanos.add(nanos);
	}

	void addEquationCopy(long nanos) {
		if(!ENABLED) {
			return;
		}
		equationCopyNanos.add(nanos);
	}

	void addEquationCopyBreakdown(long create, long store) {
		if(!ENABLED) {
			return;
		}
		equationCreateNanos.add(create);
		equationStoreNanos.add(store);
	}

	private String formatSummary() {
		return "\nKLUSolveX profile"
				+ "\n  handles=" + handles.sum() + ", handle_ms=" + ms(handleNanos)
				+ "\n  options=" + options.sum() + ", option_ms=" + ms(optionNanos)
				+ "\n  matrix_full_builds=" + fullBuilds.sum()
				+ ", matrix_reuse_builds=" + reuseBuilds.sum()
				+ ", matrix_reuse_fallbacks=" + reuseFallbacks.sum()
				+ ", matrix_nonzeros_seen=" + nonzeros.sum()
				+ "\n  matrix_traversal_ms=" + ms(matrixTraversalNanos)
				+ ", native_matrix_ms=" + ms(nativeMatrixNanos)
				+ ", set_calls=" + setCalls.sum()
				+ ", primitive_calls=" + primitiveCalls.sum()
				+ ", zeroise_calls=" + zeroiseCalls.sum()
				+ ", increment_calls=" + incrementCalls.sum()
				+ "\n  factor_calls=" + factorCalls.sum()
				+ ", factor_ms=" + ms(factorNanos)
				+ "\n  solve_calls=" + solveCalls.sum()
				+ ", rhs_pack_ms=" + ms(rhsPackNanos)
				+ ", rhs_collect_ms=" + ms(rhsCollectNanos)
				+ ", rhs_native_write_ms=" + ms(rhsNativeWriteNanos)
				+ ", native_solve_ms=" + ms(nativeSolveNanos)
				+ ", result_unpack_ms=" + ms(resultUnpackNanos)
				+ "\n  complex_result_ms=" + ms(complexResultNanos)
				+ ", equation_result_copy_ms=" + ms(equationCopyNanos)
				+ ", equation_result_create_ms=" + ms(equationCreateNanos)
				+ ", equation_result_store_ms=" + ms(equationStoreNanos);
	}

	private String ms(LongAdder nanos) {
		return String.format("%.3f", nanos.sum() / 1_000_000.0);
	}
}
