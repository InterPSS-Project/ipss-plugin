package org.interpss.threePhase.dataParser.opendss;

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
		return calcVoltageBases();
	}

	@Override
	public boolean calcVoltageBases() {
		return super.calcVoltageBases();
	}

}
