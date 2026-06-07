package org.interpss.threePhase.dataParser.opendss;

import org.ieee.odm.common.ODMLogger;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.core.net.NetworkType;

public class OpenDSSStaticDataParser extends OpenDSSDataParser {

	public OpenDSSStaticDataParser() {
		super(false);
		this.staticNet = ThreePhaseObjectFactory.createStatic3PhaseNetwork();
		this.staticNet.setNetworkType(NetworkType.DISTRIBUTION);
	}

	@Override
	public boolean isStaticNetworkMode() {
		return true;
	}

	@Override
	public DStabNetwork3Phase getDistNetwork() {
		throw new UnsupportedOperationException("OpenDSSStaticDataParser does not expose a DStab network");
	}

	@Override
	public void setDistNetwork(DStabNetwork3Phase distNet) {
		throw new UnsupportedOperationException("OpenDSSStaticDataParser does not accept a DStab network");
	}

	@Override
	public boolean hasDistNetwork() {
		return false;
	}

	@Override
	public boolean initNetwork() {
		boolean noError = true;
		try {
			this.xfrParser.mergeParallelSinglePhaseRegulatorBranches();
		} catch (Exception e) {
			ODMLogger.getLogger().severe("Failed to merge parallel single-phase transformer regulators: " + e.toString());
			noError = false;
		}
		if(noError && this.isRegControlEnabled()) {
			this.regulatorParser.applyFixedRegControlRatios();
		}
		return noError;
	}

	@Override
	public boolean calcVoltageBases() {
		return initNetwork();
	}

}
