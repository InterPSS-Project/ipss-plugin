package org.interpss.plugin.sensitivity;

import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.CandidateType;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.GeneratedDirectionSet;
import org.interpss.plugin.sensitivity.DcSensitivityStudyDefinition.MonitorSet;

import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;

/**
 * Cheap, non-materializing upper bound for the number of factor candidates a
 * portable study will evaluate. External adapters use it to avoid accidentally
 * materializing very large result sets in memory.
 */
public final class DcSensitivityStudyEstimator {
	/** Largest candidate set suitable for an inline API/CLI/UI response by default. */
	public static final long MAX_INLINE_RESULT_CANDIDATES = 100_000L;

	private DcSensitivityStudyEstimator() {}

	public static long estimateCandidateCount(BaseAclfNetwork<?, ?> network,
			DcSensitivityStudyDefinition study) {
		if (network == null || study == null) throw new IllegalArgumentException("Network and study are required");
		long count = 0;
		for (var spec : study.analyses()) {
			long specCount;
			if (spec instanceof DcSensitivityStudyDefinition.PtdfSpec ptdf) {
				specCount = multiply(directionCount(ptdf), monitorCount(network, ptdf.monitors()));
			} else if (spec instanceof DcSensitivityStudyDefinition.ShiftFactorSpec shiftFactor) {
				specCount = multiply(shiftCandidateCount(network, study, shiftFactor),
						monitorCount(network, shiftFactor.monitors()));
			} else if (spec instanceof DcSensitivityStudyDefinition.LodfSpec lodf) {
				specCount = multiply(lodf.outageBranchIds().size(), monitorCount(network, lodf.monitors()));
			} else if (spec instanceof DcSensitivityStudyDefinition.MultiOutageLodfSpec multi) {
				specCount = multiply(multi.outageGroups().stream().mapToLong(group -> group.branchIds().size()).sum(),
						monitorCount(network, multi.monitors()));
			} else {
				throw new IllegalArgumentException("Unsupported sensitivity specification " + spec.getClass().getName());
			}
			count = add(count, specCount);
		}
		return count;
	}

	private static long directionCount(DcSensitivityStudyDefinition.PtdfSpec spec) {
		long count = spec.directions().stream().filter(direction -> direction.included()).count();
		for (GeneratedDirectionSet set : spec.generatedDirections()) {
			long generated = switch (set.expansion()) {
				case CARTESIAN -> multiply(set.sources().size(), set.sinks().size());
				case PAIRED -> set.sources().size();
			};
			count = add(count, generated);
		}
		return count;
	}

	private static long shiftCandidateCount(BaseAclfNetwork<?, ?> network,
			DcSensitivityStudyDefinition study, DcSensitivityStudyDefinition.ShiftFactorSpec spec) {
		long count = spec.explicitCandidates().size();
		for (CandidateType type : spec.candidateTypes()) {
			long generated = switch (type) {
				case BUS -> network.getBusList().stream().filter(BaseAclfBus::isActive).count();
				case GENERATOR -> network.getBusList().stream().filter(BaseAclfBus::isActive)
						.mapToLong(bus -> bus.getContributeGenList().stream().filter(AclfGen::isActive).count()).sum();
				case LOAD -> network.getBusList().stream().filter(BaseAclfBus::isActive)
						.mapToLong(bus -> bus.getContributeLoadList().stream().filter(AclfLoad::isActive).count()).sum();
				case AREA -> network.getAreaMap().size();
				case ZONE -> network.getZoneMap().size();
				case SUPER_AREA -> study.endpoints().superAreas().size();
				case INJECTION_GROUP -> study.endpoints().injectionGroups().size();
			};
			count = add(count, generated);
		}
		return count;
	}

	private static long monitorCount(BaseAclfNetwork<?, ?> network, MonitorSet monitors) {
		if (monitors.branchIds().isEmpty() && monitors.interfaces().isEmpty()) {
			return network.getBranchList().stream().filter(branch -> branch.isActive()).count();
		}
		return add(monitors.branchIds().size(), monitors.interfaces().size());
	}

	private static long add(long left, long right) {
		if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
		return left + right;
	}

	private static long multiply(long left, long right) {
		if (left == 0 || right == 0) return 0;
		if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
		return left * right;
	}
}
