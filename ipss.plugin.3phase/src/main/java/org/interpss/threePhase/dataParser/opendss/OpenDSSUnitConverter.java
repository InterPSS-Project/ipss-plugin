package org.interpss.threePhase.dataParser.opendss;

final class OpenDSSUnitConverter {

	private OpenDSSUnitConverter() {
	}

	static double lengthFactor(String fromUnit, String toUnit) {
		String from = normalizeLengthUnit(fromUnit);
		String to = normalizeLengthUnit(toUnit);
		if (from.equals("") || from.equals("none")) {
			return 1.0;
		}
		if (to.equals("")) {
			to = "mi";
		}
		if (to.equals("none") || from.equals(to)) {
			return 1.0;
		}
		return metersPerUnit(from) / metersPerUnit(to);
	}

	private static String normalizeLengthUnit(String unit) {
		if (unit == null) {
			return "";
		}
		String value = unit.trim().toLowerCase();
		if (value.equals("mile") || value.equals("miles") || value.equals("mi")) {
			return "mi";
		}
		if (value.equals("kft") || value.equals("kfeet")) {
			return "kft";
		}
		if (value.equals("ft") || value.equals("feet") || value.equals("foot")) {
			return "ft";
		}
		if (value.equals("in") || value.equals("inch") || value.equals("inches")) {
			return "in";
		}
		if (value.equals("cm") || value.equals("centimeter") || value.equals("centimeters")) {
			return "cm";
		}
		if (value.equals("km") || value.equals("kmeters")) {
			return "km";
		}
		if (value.equals("m") || value.equals("meter") || value.equals("meters")) {
			return "m";
		}
		if (value.equals("none")) {
			return "none";
		}
		return value;
	}

	private static double metersPerUnit(String unit) {
		switch (unit) {
		case "mi":
			return 1609.344;
		case "kft":
			return 304.8;
		case "ft":
			return 0.3048;
		case "in":
			return 0.0254;
		case "cm":
			return 0.01;
		case "km":
			return 1000.0;
		case "m":
			return 1.0;
		default:
			throw new IllegalArgumentException("Unsupported OpenDSS length unit: " + unit);
		}
	}
}
