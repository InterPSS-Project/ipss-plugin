package org.interpss.plugin.sensitivity;

import com.interpss.core.aclf.BaseAclfNetwork;

/** Executes a portable DC sensitivity definition against an already loaded network. */
public interface DcSensitivityRunner {
	/**
	 * Runs the study and streams retained rows to {@code sink}.
	 *
	 * <p>The default implementation serializes runs on the network instance. It may
	 * temporarily disable non-reference islands and always restores their original
	 * status before returning. Thread interruption is treated as cooperative
	 * cancellation and produces an incomplete result manifest.</p>
	 */
	SensitivityResult.Manifest run(
			BaseAclfNetwork<?, ?> network,
			DcSensitivityStudyDefinition study,
			SensitivityResultSink sink);
}
