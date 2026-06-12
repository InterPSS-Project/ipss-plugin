package org.interpss.threePhase.dataParser.opendss;

import org.ieee.odm.common.ODMLogger;

public class OpenDSSWireDataParser {

	private final OpenDSSDataParser dataParser;

	public OpenDSSWireDataParser(OpenDSSDataParser parser) {
		this.dataParser = parser;
	}

	public boolean parseWireData(String wireDataStr) {
		try {
			String[] tokens = wireDataStr.trim().split("\\s+");
			String id = "";
			double rdc = 0.0;
			double rac = 0.0;
			String rUnits = "mi";
			double gmrAc = 0.0;
			String gmrUnits = "ft";
			double radius = 0.0;
			String radiusUnits = "ft";
			double normAmps = 0.0;

			for (String token : tokens) {
				String lowerToken = token.toLowerCase();
				if (lowerToken.contains("wiredata.")) {
					id = token.substring(token.indexOf(".") + 1).toLowerCase();
				}
				else if (lowerToken.startsWith("rdc=")) {
					rdc = Double.valueOf(token.substring(token.indexOf("=") + 1));
				}
				else if (lowerToken.startsWith("rac=")) {
					rac = Double.valueOf(token.substring(token.indexOf("=") + 1));
				}
				else if (lowerToken.startsWith("runits=")) {
					rUnits = token.substring(token.indexOf("=") + 1);
				}
				else if (lowerToken.startsWith("gmrac=")) {
					gmrAc = Double.valueOf(token.substring(token.indexOf("=") + 1));
				}
				else if (lowerToken.startsWith("gmrunits=")) {
					gmrUnits = token.substring(token.indexOf("=") + 1);
				}
				else if (lowerToken.startsWith("radius=") || lowerToken.startsWith("rad=")) {
					radius = Double.valueOf(token.substring(token.indexOf("=") + 1));
				}
				else if (lowerToken.startsWith("diam=") || lowerToken.startsWith("diameter=")) {
					radius = 0.5 * Double.valueOf(token.substring(token.indexOf("=") + 1));
				}
				else if (lowerToken.startsWith("radunits=")) {
					radiusUnits = token.substring(token.indexOf("=") + 1);
				}
				else if (lowerToken.startsWith("normamps=")) {
					normAmps = Double.valueOf(token.substring(token.indexOf("=") + 1));
				}
			}

			if (id.equals("")) {
				throw new IllegalArgumentException("WireData id is missing: " + wireDataStr);
			}
			if (rac <= 0.0 || gmrAc <= 0.0) {
				throw new IllegalArgumentException("WireData Rac and GMRac must be positive: " + wireDataStr);
			}
			dataParser.getWireDataTable().put(id, new OpenDSSWireData(id, rdc, rac, rUnits, gmrAc, gmrUnits,
					radius, radiusUnits, normAmps));
			return true;
		} catch (Exception e) {
			ODMLogger.getLogger().severe(e.toString());
			e.printStackTrace();
			return false;
		}
	}
}
