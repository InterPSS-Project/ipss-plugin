package org.interpss.threePhase.dataParser.opendss;

import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.common.ODMLogger;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.LineConfiguration;

public class OpenDSSLineGeometryParser {

	private static final double CARSON_R_OHM_PER_MILE = 0.0953;
	private static final double CARSON_X_COEFF = 0.12134;
	private static final double CARSON_LOG_CONSTANT = 7.93402;
	private static final double FEET_TO_METER = 0.3048;
	private static final double MILE_TO_METER = 1609.344;
	private static final double VACUUM_PERMITTIVITY = 8.854187817e-12;

	private final OpenDSSDataParser dataParser;

	public OpenDSSLineGeometryParser(OpenDSSDataParser parser) {
		this.dataParser = parser;
	}

	public boolean parseLineGeometryBlock(List<String> geometryLines) {
		try {
			String id = "";
			int nconds = 0;
			int nphases = 0;
			boolean reduce = false;
			ConductorPosition[] positions = new ConductorPosition[0];

			for (String rawLine : geometryLines) {
				String str = stripInlineComment(rawLine).trim();
				if (str.equals("")) {
					continue;
				}
				if (str.startsWith("~")) {
					str = str.substring(1).trim();
				}
				String lowerStr = str.toLowerCase();
				String[] tokens = str.split("\\s+");

				if (lowerStr.startsWith("new")) {
					for (String token : tokens) {
						String lowerToken = token.toLowerCase();
						if (lowerToken.contains("linegeometry.")) {
							id = token.substring(token.indexOf(".") + 1).toLowerCase();
						}
						else if (lowerToken.startsWith("nconds=")) {
							nconds = Integer.valueOf(token.substring(token.indexOf("=") + 1));
						}
						else if (lowerToken.startsWith("nphases=")) {
							nphases = Integer.valueOf(token.substring(token.indexOf("=") + 1));
						}
						else if (lowerToken.startsWith("reduce=")) {
							reduce = isYes(token.substring(token.indexOf("=") + 1));
						}
					}
					positions = new ConductorPosition[nconds];
				}
				else {
					int cond = getIntValue(tokens, "cond=", -1);
					if (cond > 0) {
						String wireId = getStringValue(tokens, "wire=", "").toLowerCase();
						String units = getStringValue(tokens, "units=", "ft");
						double xFeet = getDoubleValue(tokens, "x=", 0.0) * OpenDSSUnitConverter.lengthFactor(units, "ft");
						double hFeet = getDoubleValue(tokens, "h=", 0.0) * OpenDSSUnitConverter.lengthFactor(units, "ft");
						positions[cond - 1] = new ConductorPosition(wireId, xFeet, hFeet);
					}
					if (lowerStr.contains("reduce=")) {
						reduce = isYes(getStringValue(tokens, "reduce=", "no"));
					}
				}
			}

			if (id.equals("") || nconds <= 0 || nphases <= 0) {
				throw new Error("LineGeometry header is incomplete: " + geometryLines);
			}

			Complex[][] zCond = buildCarsonMatrix(positions);
			Complex[][] zPhase = reduce && nconds > nphases ? kronReduce(zCond, nphases) : leadingSubMatrix(zCond, nphases);
			Complex[][] cPhase = leadingSubMatrix(buildCapacitanceMatrix(positions), nphases);

			LineConfiguration config = new LineConfiguration();
			config.setId(id);
			config.setNphases(nphases);
			config.setLengthUnit("mi");
			config.setZ3x3Matrix(toComplex3x3(zPhase, nphases));
			config.setShuntY3x3Matrix(toComplex3x3(cPhase, nphases));
			dataParser.getLineConfigTable().put(id, config);
			return true;
		} catch (Exception e) {
			ODMLogger.getLogger().severe(e.toString());
			e.printStackTrace();
			return false;
		}
	}

	private Complex[][] buildCarsonMatrix(ConductorPosition[] positions) {
		int n = positions.length;
		Complex[][] z = new Complex[n][n];
		for (int i = 0; i < n; i++) {
			OpenDSSWireData wireI = dataParser.getWireDataTable().get(positions[i].wireId);
			if (wireI == null) {
				throw new Error("WireData definition not found: " + positions[i].wireId);
			}
			for (int j = 0; j < n; j++) {
				if (i == j) {
					double gmr = Math.max(wireI.getGmrFeet(), 1.0e-9);
					z[i][j] = new Complex(
							wireI.getRacOhmPerMile() + CARSON_R_OHM_PER_MILE,
							CARSON_X_COEFF * (Math.log(1.0 / gmr) + CARSON_LOG_CONSTANT));
				}
				else {
					double distance = Math.hypot(positions[i].xFeet - positions[j].xFeet, positions[i].hFeet - positions[j].hFeet);
					distance = Math.max(distance, 1.0e-9);
					z[i][j] = new Complex(
							CARSON_R_OHM_PER_MILE,
							CARSON_X_COEFF * (Math.log(1.0 / distance) + CARSON_LOG_CONSTANT));
				}
			}
		}
		return z;
	}

	private Complex[][] buildCapacitanceMatrix(ConductorPosition[] positions) {
		int n = positions.length;
		Complex[][] potential = new Complex[n][n];
		for (int i = 0; i < n; i++) {
			OpenDSSWireData wireI = dataParser.getWireDataTable().get(positions[i].wireId);
			if (wireI == null) {
				throw new Error("WireData definition not found: " + positions[i].wireId);
			}
			if (!wireI.hasRadius()) {
				return zeroMatrix(n);
			}
			for (int j = 0; j < n; j++) {
				double xi = positions[i].xFeet * FEET_TO_METER;
				double hi = positions[i].hFeet * FEET_TO_METER;
				double xj = positions[j].xFeet * FEET_TO_METER;
				double hj = positions[j].hFeet * FEET_TO_METER;
				if (i == j) {
					double radius = Math.max(wireI.getRadiusFeet() * FEET_TO_METER, 1.0e-12);
					potential[i][j] = new Complex(Math.log(2.0 * Math.max(hi, 1.0e-12) / radius), 0.0);
				}
				else {
					double conductorDistance = Math.max(Math.hypot(xi - xj, hi - hj), 1.0e-12);
					double imageDistance = Math.max(Math.hypot(xi - xj, hi + hj), 1.0e-12);
					potential[i][j] = new Complex(Math.log(imageDistance / conductorDistance), 0.0);
				}
			}
		}
		Complex[][] inversePotential = invert(potential);
		double nfPerMileFactor = 2.0 * Math.PI * VACUUM_PERMITTIVITY * MILE_TO_METER * 1.0e9;
		Complex[][] capacitance = new Complex[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				capacitance[i][j] = new Complex(0.0, inversePotential[i][j].getReal() * nfPerMileFactor);
			}
		}
		return capacitance;
	}

	private static Complex[][] zeroMatrix(int size) {
		Complex[][] zero = new Complex[size][size];
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				zero[i][j] = Complex.ZERO;
			}
		}
		return zero;
	}

	private static Complex[][] kronReduce(Complex[][] z, int nphases) {
		int neutralCount = z.length - nphases;
		Complex[][] reduced = new Complex[nphases][nphases];
		Complex[][] znnInv = invert(subMatrix(z, nphases, z.length, nphases, z.length));
		for (int i = 0; i < nphases; i++) {
			for (int j = 0; j < nphases; j++) {
				Complex correction = Complex.ZERO;
				for (int m = 0; m < neutralCount; m++) {
					for (int n = 0; n < neutralCount; n++) {
						correction = correction.add(z[i][nphases + m].multiply(znnInv[m][n]).multiply(z[nphases + n][j]));
					}
				}
				reduced[i][j] = z[i][j].subtract(correction);
			}
		}
		return reduced;
	}

	private static Complex[][] invert(Complex[][] matrix) {
		int n = matrix.length;
		Complex[][] a = new Complex[n][2 * n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				a[i][j] = matrix[i][j];
			}
			for (int j = 0; j < n; j++) {
				a[i][n + j] = i == j ? Complex.ONE : Complex.ZERO;
			}
		}
		for (int p = 0; p < n; p++) {
			int pivot = p;
			for (int r = p + 1; r < n; r++) {
				if (a[r][p].abs() > a[pivot][p].abs()) {
					pivot = r;
				}
			}
			if (a[pivot][p].abs() < 1.0e-12) {
				throw new Error("Singular neutral impedance block in LineGeometry");
			}
			if (pivot != p) {
				Complex[] tmp = a[p];
				a[p] = a[pivot];
				a[pivot] = tmp;
			}
			Complex pivotValue = a[p][p];
			for (int c = 0; c < 2 * n; c++) {
				a[p][c] = a[p][c].divide(pivotValue);
			}
			for (int r = 0; r < n; r++) {
				if (r != p) {
					Complex factor = a[r][p];
					for (int c = 0; c < 2 * n; c++) {
						a[r][c] = a[r][c].subtract(factor.multiply(a[p][c]));
					}
				}
			}
		}
		Complex[][] inv = new Complex[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				inv[i][j] = a[i][n + j];
			}
		}
		return inv;
	}

	private static Complex[][] subMatrix(Complex[][] matrix, int rowFrom, int rowTo, int colFrom, int colTo) {
		Complex[][] sub = new Complex[rowTo - rowFrom][colTo - colFrom];
		for (int i = rowFrom; i < rowTo; i++) {
			for (int j = colFrom; j < colTo; j++) {
				sub[i - rowFrom][j - colFrom] = matrix[i][j];
			}
		}
		return sub;
	}

	private static Complex[][] leadingSubMatrix(Complex[][] matrix, int size) {
		return subMatrix(matrix, 0, size, 0, size);
	}

	private static Complex3x3 toComplex3x3(Complex[][] z, int nphases) throws Exception {
		Complex3x3 z3 = new Complex3x3();
		if (nphases >= 1) {
			z3.aa = z[0][0];
		}
		if (nphases >= 2) {
			z3.ab = z[0][1];
			z3.ba = z[1][0];
			z3.bb = z[1][1];
		}
		if (nphases == 3) {
			z3.ac = z[0][2];
			z3.ca = z[2][0];
			z3.bc = z[1][2];
			z3.cb = z[2][1];
			z3.cc = z[2][2];
		}
		else if (nphases > 3) {
			throw new Exception("nphases > 3 not supported yet");
		}
		return z3;
	}

	private static String stripInlineComment(String value) {
		int commentIdx = value.indexOf("!");
		return commentIdx > 0 ? value.substring(0, commentIdx) : value;
	}

	private static boolean isYes(String value) {
		String lowerValue = value.toLowerCase();
		return lowerValue.equals("yes") || lowerValue.equals("y") || lowerValue.equals("true");
	}

	private static int getIntValue(String[] tokens, String key, int defaultValue) {
		String value = getTokenValue(tokens, key);
		return value == null ? defaultValue : Integer.valueOf(value);
	}

	private static double getDoubleValue(String[] tokens, String key, double defaultValue) {
		String value = getTokenValue(tokens, key);
		return value == null ? defaultValue : Double.valueOf(value);
	}

	private static String getStringValue(String[] tokens, String key, String defaultValue) {
		String value = getTokenValue(tokens, key);
		return value == null ? defaultValue : value;
	}

	private static String getTokenValue(String[] tokens, String key) {
		String normalizedKey = key.toLowerCase().replace("=", "");
		for (int i = 0; i < tokens.length; i++) {
			String token = tokens[i].trim();
			String lowerToken = token.toLowerCase();
			if (lowerToken.startsWith(normalizedKey + "=")) {
				return token.substring(token.indexOf("=") + 1);
			}
			if (lowerToken.equals(normalizedKey) && i + 1 < tokens.length) {
				String next = tokens[i + 1].trim();
				if (next.startsWith("=")) {
					return next.substring(1);
				}
			}
			if (lowerToken.equals(normalizedKey) && i + 2 < tokens.length && tokens[i + 1].trim().equals("=")) {
				return tokens[i + 2].trim();
			}
		}
		return null;
	}

	private static final class ConductorPosition {
		private final String wireId;
		private final double xFeet;
		private final double hFeet;

		private ConductorPosition(String wireId, double xFeet, double hFeet) {
			this.wireId = wireId;
			this.xFeet = xFeet;
			this.hFeet = hFeet;
		}
	}
}
