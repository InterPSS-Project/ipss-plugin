package org.interpss.threePhase.qsts.opendss;

import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileBinding;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileType;
import org.interpss.threePhase.qsts.QstsLoadMultiplier;
import org.interpss.threePhase.qsts.QstsLoadMultiplierResolver;
import org.interpss.threePhase.qsts.QstsMode;
import org.interpss.threePhase.qsts.QstsProfileBinding;
import org.interpss.threePhase.qsts.QstsProfileRegistry;

public class OpenDSSLoadProfileAdapter {
	private final QstsLoadMultiplierResolver resolver;

	public OpenDSSLoadProfileAdapter(QstsProfileRegistry profileRegistry) {
		this.resolver = new QstsLoadMultiplierResolver(profileRegistry);
	}

	public QstsLoadMultiplier resolve(OpenDSSProfileBinding binding, QstsMode mode, int stepIndex,
			double globalLoadMult) {
		QstsProfileBinding qstsBinding = binding == null ? null : binding.toQstsProfileBinding();
		return resolver.resolve(qstsBinding, mode, stepIndex, globalLoadMult);
	}

	public String profileId(OpenDSSProfileBinding binding, OpenDSSProfileType type) {
		return binding == null ? null : binding.getShapeId(type);
	}
}
