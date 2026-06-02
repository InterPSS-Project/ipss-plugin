package org.interpss.threePhase.dataParser.opendss;

final class OpenDSSWireData {

	private final String id;
	private final double rac;
	private final String rUnits;
	private final double gmrAc;
	private final String gmrUnits;
	private final double normAmps;

	OpenDSSWireData(String id, double rac, String rUnits, double gmrAc, String gmrUnits, double normAmps) {
		this.id = id;
		this.rac = rac;
		this.rUnits = rUnits;
		this.gmrAc = gmrAc;
		this.gmrUnits = gmrUnits;
		this.normAmps = normAmps;
	}

	String getId() {
		return id;
	}

	double getRacOhmPerMile() {
		return rac * OpenDSSUnitConverter.lengthFactor("mi", rUnits);
	}

	double getGmrFeet() {
		return gmrAc * OpenDSSUnitConverter.lengthFactor(gmrUnits, "ft");
	}

	double getNormAmps() {
		return normAmps;
	}
}
