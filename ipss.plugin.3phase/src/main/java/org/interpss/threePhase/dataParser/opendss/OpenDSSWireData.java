package org.interpss.threePhase.dataParser.opendss;

final class OpenDSSWireData {

	private final String id;
	private final double rdc;
	private final double rac;
	private final String rUnits;
	private final double gmrAc;
	private final String gmrUnits;
	private final double radius;
	private final String radiusUnits;
	private final double normAmps;

	OpenDSSWireData(String id, double rdc, double rac, String rUnits, double gmrAc, String gmrUnits,
			double radius, String radiusUnits, double normAmps) {
		this.id = id;
		this.rdc = rdc;
		this.rac = rac;
		this.rUnits = rUnits;
		this.gmrAc = gmrAc;
		this.gmrUnits = gmrUnits;
		this.radius = radius;
		this.radiusUnits = radiusUnits;
		this.normAmps = normAmps;
	}

	String getId() {
		return id;
	}

	double getRacOhmPerMile() {
		return rac * OpenDSSUnitConverter.lengthFactor("mi", rUnits);
	}

	double getRdcOhmPerMile() {
		double value = rdc > 0.0 ? rdc : rac / 1.02;
		return value * OpenDSSUnitConverter.lengthFactor("mi", rUnits);
	}

	double getGmrFeet() {
		return gmrAc * OpenDSSUnitConverter.lengthFactor(gmrUnits, "ft");
	}

	double getRadiusFeet() {
		return radius * OpenDSSUnitConverter.lengthFactor(radiusUnits, "ft");
	}

	boolean hasRadius() {
		return radius > 0.0;
	}

	double getNormAmps() {
		return normAmps;
	}
}
