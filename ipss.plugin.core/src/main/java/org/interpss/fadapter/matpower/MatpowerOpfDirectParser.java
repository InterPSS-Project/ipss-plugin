/*
 * @(#)MatpowerOpfDirectParser.java
 *
 * Copyright (C) 2006-2026 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 */

package org.interpss.fadapter.matpower;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Point;
import org.interpss.plugin.opf.util.MatpowerDcLineData;
import org.interpss.plugin.opf.util.OpfHvdcPreprocessor;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.HvdcObjectFactory;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.common.curve.CommonCurveFactory;
import com.interpss.core.common.curve.NumericCurveModel;
import com.interpss.core.common.curve.PieceWiseCurve;
import com.interpss.core.common.curve.QuadraticCurve;
import com.interpss.opf.OpfBranch;
import com.interpss.opf.OpfBus;
import com.interpss.opf.OpfGen;
import com.interpss.opf.OpfGenOperatingMode;
import com.interpss.opf.OpfNetwork;
import com.interpss.opf.OpfObjectFactory;
import com.interpss.opf.datatype.IncrementalCost;
import com.interpss.opf.datatype.OpfBusLimits;
import com.interpss.opf.datatype.OpfDatatypeFactory;

/** Direct MATPOWER case parser for the InterPSS OPF network model. */
public class MatpowerOpfDirectParser {
	private static final String BUS_PREFIX = "Bus";
	private static final Pattern BASE_MVA = Pattern.compile(
			"\\bmpc\\.baseMVA\\s*=\\s*([^;]+);", Pattern.CASE_INSENSITIVE);
	private static final Pattern CASE_NAME = Pattern.compile(
			"(?m)^\\s*function\\s+\\w+\\s*=\\s*([A-Za-z][A-Za-z0-9_]*)");
	private static final Pattern QUOTED_STRING = Pattern.compile("'((?:''|[^'])*)'");

	public OpfNetwork parse(String filepath) throws InterpssException {
		try {
			String source = stripComments(Files.readString(Path.of(filepath)));
			return build(source, parseBaseMva(source));
		} catch (IOException e) {
			throw new InterpssException("Error reading MATPOWER OPF file " + filepath + ": " + e.getMessage());
		} catch (RuntimeException e) {
			throw new InterpssException("Error parsing MATPOWER OPF file " + filepath + ": " + e.getMessage());
		}
	}

	private static OpfNetwork build(String source, double baseMva) throws InterpssException {
		List<double[]> busData = matrix(source, "bus", true);
		List<double[]> genData = matrix(source, "gen", true);
		List<double[]> branchData = matrix(source, "branch", true);
		List<double[]> costData = matrix(source, "gencost", false);
		List<double[]> dcLineData = matrix(source, "dcline", false);
		List<List<String>> busNames = stringMatrix(source, "bus_name");
		List<List<String>> generatorNames = stringMatrix(source, "gen_name");
		List<List<String>> generatorTypes = stringMatrix(source, "gentype");
		List<List<String>> generatorFuels = stringMatrix(source, "genfuel");
		List<List<String>> branchNames = stringMatrix(source, "branch_name");

		OpfNetwork network = OpfObjectFactory.createOpfNetwork();
		network.setId(caseName(source));
		network.setName(caseName(source));
		network.setBaseKva(baseMva * 1000.0);

		Map<Integer, OpfBus> buses = buildBuses(network, busData, busNames, baseMva);
		List<OpfGen> generators = buildGenerators(genData, generatorNames, generatorTypes,
				generatorFuels, buses, baseMva);
		applyCosts(generators, costData, baseMva);
		buildBranches(network, branchData, branchNames, buses, baseMva);
		buildDcLines(network, dcLineData, buses);
		OpfHvdcPreprocessor.preprocess(network);
		return network;
	}

	private static Map<Integer, OpfBus> buildBuses(OpfNetwork network, List<double[]> busData,
			List<List<String>> busNames, double baseMva) throws InterpssException {
		Map<Integer, OpfBus> buses = new LinkedHashMap<>();
		for (int index = 0; index < busData.size(); index++) {
			double[] row = busData.get(index);
			requireColumns(row, 13, "bus", index);
			int number = integer(row[0], "bus number");
			OpfBus bus = OpfObjectFactory.createOpfBus(BUS_PREFIX + number, network);
			bus.setNumber(number);
			bus.setName(stringValue(busNames, index, 0, BUS_PREFIX + number));
			bus.setBaseVoltage(row[9] * 1000.0);
			bus.setVoltageMag(row[7]);
			bus.setVoltageAng(Math.toRadians(row[8]));
			bus.setVLimit(new LimitType(row[11], row[12]));
			bus.setLoadP(row[2] / baseMva);
			bus.setLoadQ(row[3] / baseMva);
			bus.setLoadCode(row[2] != 0.0 || row[3] != 0.0 ? AclfLoadCode.CONST_P : AclfLoadCode.NON_LOAD);
			if (row[4] != 0.0 || row[5] != 0.0) {
				bus.setShuntY(new Complex(row[4] / baseMva, row[5] / baseMva));
			}
			int type = integer(row[1], "bus type");
			bus.setStatus(type != 4);
			bus.setGenCode(type == 3 ? AclfGenCode.SWING
					: type == 2 ? AclfGenCode.GEN_PV : AclfGenCode.NON_GEN);
			if (buses.put(number, bus) != null) {
				throw new IllegalArgumentException("Duplicate MATPOWER bus number: " + number);
			}
		}
		return buses;
	}

	private static List<OpfGen> buildGenerators(List<double[]> genData,
			List<List<String>> generatorNames, List<List<String>> generatorTypes,
			List<List<String>> generatorFuels, Map<Integer, OpfBus> buses, double baseMva)
			throws InterpssException {
		Map<Integer, Integer> generatorCount = new HashMap<>();
		List<OpfGen> generators = new ArrayList<>();
		for (int index = 0; index < genData.size(); index++) {
			double[] row = genData.get(index);
			requireColumns(row, 10, "gen", index);
			int busNumber = integer(row[0], "generator bus number");
			OpfBus bus = requiredBus(buses, busNumber, "generator");
			int ordinal = generatorCount.merge(busNumber, 1, Integer::sum);
			String id = BUS_PREFIX + busNumber + "-G" + ordinal;
			OpfGen generator = OpfObjectFactory.createOpfGen(id);
			generator.setName(stringValue(generatorNames, index, 0, id));
			String type = firstNonBlank(stringValue(generatorNames, index, 1, ""),
					stringValue(generatorTypes, index, 0, ""));
			String fuel = firstNonBlank(stringValue(generatorNames, index, 2, ""),
					stringValue(generatorFuels, index, 0, ""));
			generator.setDesc(generatorDescription(type, fuel));
			generator.setCoeffA(0.0);
			generator.setCoeffB(0.0);
			generator.setFixedCost(0.0);
			generator.setGen(new Complex(row[1] / baseMva, row[2] / baseMva));
			generator.setQGenLimit(new LimitType(row[3] / baseMva, row[4] / baseMva));
			generator.setDesiredVoltMag(row[5]);
			generator.setMvaBase(row[6] == 0.0 ? baseMva : row[6]);
			generator.setPGenLimit(new LimitType(row[8] / baseMva, row[9] / baseMva));
			generator.setOperatingMode(generatorOperatingMode(bus));
			generator.setCode(bus.getGenCode() == AclfGenCode.GEN_PV || bus.getGenCode() == AclfGenCode.SWING
					? AclfGenCode.GEN_PV : AclfGenCode.GEN_PQ);

			OpfBusLimits limits = OpfDatatypeFactory.eINSTANCE.createOpfBusLimits();
			limits.setPLimit(new LimitType(row[8] / baseMva, row[9] / baseMva));
			limits.setQLimit(new LimitType(row[3] / baseMva, row[4] / baseMva));
			generator.setOpfLimits(limits);

			boolean active = row[7] > 0.0;
			generator.setStatus(active);
			Map<String, Double> attributes = new HashMap<>();
			attributes.put("status", active ? 1.0 : 0.0);
			if (isStorage(type, fuel)) {
				attributes.put("storage", 1.0);
			}
			generator.setAdditionalAttributes(attributes);
			bus.getContributeGenList().add(generator);
			if (active) {
				bus.setGenP(bus.getGenP() + row[1] / baseMva);
				bus.setGenQ(bus.getGenQ() + row[2] / baseMva);
				if (bus.getGenCode() == AclfGenCode.NON_GEN) {
					bus.setGenCode(AclfGenCode.GEN_PQ);
				}
			}
			generators.add(generator);
		}
		return generators;
	}

	private static void buildBranches(OpfNetwork network, List<double[]> branchData,
			List<List<String>> branchNames, Map<Integer, OpfBus> buses, double baseMva)
			throws InterpssException {
		Map<String, Integer> circuitCount = new HashMap<>();
		for (int index = 0; index < branchData.size(); index++) {
			double[] row = branchData.get(index);
			requireColumns(row, 11, "branch", index);
			int from = integer(row[0], "from bus number");
			int to = integer(row[1], "to bus number");
			requiredBus(buses, from, "branch");
			requiredBus(buses, to, "branch");
			String key = from + "-" + to;
			String circuit = String.valueOf(circuitCount.merge(key, 1, Integer::sum));
			OpfBranch branch = OpfObjectFactory.createOpfBranch();
			branch.setName(stringValue(branchNames, index, 0, ""));
			branch.setZ(new Complex(row[2], row[3]));
			branch.setHShuntY(new Complex(0.0, row[4] * 0.5));
			branch.setRatingMw1(row[5] / baseMva);
			branch.setRatingMw2(row[6] / baseMva);
			branch.setRatingMw3(row[7] / baseMva);
			branch.setStatus(row[10] > 0.0);
			double ratio = row[8];
			double angle = row[9];
			if (ratio != 0.0 || angle != 0.0) {
				branch.setBranchCode(angle == 0.0 ? AclfBranchCode.XFORMER : AclfBranchCode.PS_XFORMER);
				branch.setFromTurnRatio(ratio == 0.0 ? 1.0 : ratio);
				branch.setToTurnRatio(1.0);
				branch.setFromPSXfrAngle(Math.toRadians(angle));
			} else {
				branch.setBranchCode(AclfBranchCode.LINE);
			}
			if (!network.addBranch(branch, BUS_PREFIX + from, BUS_PREFIX + to, circuit)) {
				throw new IllegalArgumentException("Failed to add MATPOWER branch " + from + "-" + to
						+ "(" + circuit + ")");
			}
		}
	}

	private static void buildDcLines(OpfNetwork network, List<double[]> dcLineData,
			Map<Integer, OpfBus> buses) {
		for (int index = 0; index < dcLineData.size(); index++) {
			double[] row = dcLineData.get(index);
			requireColumns(row, 7, "dcline", index);
			int from = integer(row[0], "DC line from bus number");
			int to = integer(row[1], "DC line to bus number");
			requiredBus(buses, from, "DC line");
			requiredBus(buses, to, "DC line");
			int lineNumber = index + 1;
			HvdcLine2TLCC<BaseAclfBus<?, ?>> dcLine = HvdcObjectFactory.createHvdcLine2TLCC();
			dcLine.setStatus(row[2] > 0.0);
			dcLine.setExtensionObject(new MatpowerDcLineData(lineNumber, row[3], row[4], row[5], row[6],
					rawRecord(row)));
			network.addHvdcLine2T(dcLine, BUS_PREFIX + from, BUS_PREFIX + to,
					String.valueOf(lineNumber), "MatpowerDcLine" + lineNumber,
					"MATPOWER DC line " + lineNumber);
		}
	}

	private static void applyCosts(List<OpfGen> generators, List<double[]> costs, double baseMva) {
		if (!costs.isEmpty() && costs.size() < generators.size()) {
			throw new IllegalArgumentException("MATPOWER gencost has " + costs.size()
					+ " rows for " + generators.size() + " generators");
		}
		for (int index = 0; index < Math.min(generators.size(), costs.size()); index++) {
			double[] row = costs.get(index);
			requireColumns(row, 4, "gencost", index);
			int model = integer(row[0], "generator cost model");
			int pointCount = integer(row[3], "generator cost point count");
			if (pointCount < 1 || row.length < 4 + (model == 1 ? pointCount * 2 : pointCount)) {
				throw new IllegalArgumentException("Invalid MATPOWER gencost row at index " + index);
			}
			OpfGen generator = generators.get(index);
			Map<String, Double> attributes = new HashMap<>(generator.getAdditionalAttributes());
			attributes.put("startupCost", row[1]);
			attributes.put("shutdownCost", row[2]);
			if (model == 1) {
				applyPiecewiseCost(generator, row, pointCount, attributes);
			} else if (model == 2) {
				applyPolynomialCost(generator, row, pointCount, baseMva, attributes);
			} else {
				throw new IllegalArgumentException("Unsupported MATPOWER gencost model: " + model);
			}
			generator.setAdditionalAttributes(Map.copyOf(attributes));
		}
	}

	private static void applyPolynomialCost(OpfGen generator, double[] row, int coefficientCount,
			double baseMva, Map<String, Double> attributes) {
		if (coefficientCount > 3) {
			throw new IllegalArgumentException("MATPOWER polynomial gencost supports at most quadratic costs");
		}
		double quadratic = coefficientCount >= 3 ? row[row.length - 3] : 0.0;
		double linear = coefficientCount >= 2 ? row[row.length - 2] : 0.0;
		double constant = row[row.length - 1];
		generator.setCoeffA(linear);
		generator.setCoeffB(quadratic * baseMva * baseMva);
		generator.setFixedCost(constant);
		attributes.put("linearCost", linear);

		IncrementalCost cost = OpfDatatypeFactory.eINSTANCE.createIncrementalCost();
		cost.setCostModel(NumericCurveModel.QUADRATIC);
		QuadraticCurve curve = CommonCurveFactory.eINSTANCE.createQuadraticCurve();
		curve.setA(quadratic * baseMva * baseMva);
		curve.setB(linear * baseMva);
		curve.setC(constant);
		cost.setQuadraticCurve(curve);
		generator.setIncCost(cost);
	}

	private static void applyPiecewiseCost(OpfGen generator, double[] row, int pointCount,
			Map<String, Double> attributes) {
		IncrementalCost cost = OpfDatatypeFactory.eINSTANCE.createIncrementalCost();
		cost.setCostModel(NumericCurveModel.PIECE_WISE);
		PieceWiseCurve curve = CommonCurveFactory.eINSTANCE.createPieceWiseCurve();
		for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
			Point point = new Point();
			point.x = row[4 + pointIndex * 2];
			point.y = row[5 + pointIndex * 2];
			curve.getPoints().add(point);
		}
		cost.setPieceWiseCurve(curve);
		generator.setIncCost(cost);
		if (pointCount >= 2) {
			double deltaMw = row[6] - row[4];
			if (Math.abs(deltaMw) > 1.0e-12) {
				double slope = (row[7] - row[5]) / deltaMw;
				generator.setCoeffA(slope);
				attributes.put("linearCost", slope);
			}
		}
	}

	private static OpfGenOperatingMode generatorOperatingMode(OpfBus bus) {
		return bus.getGenCode() == AclfGenCode.SWING || bus.getGenCode() == AclfGenCode.GEN_PV
				? OpfGenOperatingMode.PV_GENERATOR : OpfGenOperatingMode.PQ_GENERATOR;
	}

	private static String generatorDescription(String type, String fuel) {
		if (type.isBlank()) {
			return fuel.isBlank() ? "" : "fuel=" + fuel;
		}
		return fuel.isBlank() ? "type=" + type : "type=" + type + "; fuel=" + fuel;
	}

	private static boolean isStorage(String type, String fuel) {
		String value = (type + " " + fuel).toUpperCase();
		return value.contains("STORAGE") || value.contains("BATTERY");
	}

	private static String firstNonBlank(String first, String second) {
		return first == null || first.isBlank() ? second : first;
	}

	private static double parseBaseMva(String source) {
		Matcher matcher = BASE_MVA.matcher(source);
		if (!matcher.find()) {
			throw new IllegalArgumentException("MATPOWER case does not define mpc.baseMVA");
		}
		double value = parseNumber(matcher.group(1).trim());
		if (!(value > 0.0) || !Double.isFinite(value)) {
			throw new IllegalArgumentException("MATPOWER baseMVA must be positive and finite");
		}
		return value;
	}

	private static List<double[]> matrix(String source, String name, boolean required) {
		Pattern pattern = Pattern.compile("\\bmpc\\." + Pattern.quote(name)
				+ "\\s*=\\s*\\[(.*?)\\]\\s*;", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
		Matcher matcher = pattern.matcher(source);
		if (!matcher.find()) {
			if (required) {
				throw new IllegalArgumentException("MATPOWER case does not define mpc." + name);
			}
			return List.of();
		}
		List<double[]> rows = new ArrayList<>();
		for (String rawRow : matcher.group(1).split(";")) {
			String row = rawRow.trim();
			if (row.isEmpty()) {
				continue;
			}
			String[] tokens = row.split("[\\s,]+");
			double[] values = new double[tokens.length];
			for (int index = 0; index < tokens.length; index++) {
				values[index] = parseNumber(tokens[index]);
			}
			rows.add(values);
		}
		return List.copyOf(rows);
	}

	private static List<List<String>> stringMatrix(String source, String name) {
		Pattern pattern = Pattern.compile("\\bmpc\\." + Pattern.quote(name)
				+ "\\s*=\\s*\\{(.*?)\\}\\s*;", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
		Matcher matrix = pattern.matcher(source);
		if (!matrix.find()) {
			return List.of();
		}
		List<List<String>> rows = new ArrayList<>();
		for (String rawRow : matrix.group(1).split(";")) {
			Matcher value = QUOTED_STRING.matcher(rawRow);
			List<String> columns = new ArrayList<>();
			while (value.find()) {
				columns.add(value.group(1).replace("''", "'"));
			}
			if (!columns.isEmpty()) {
				rows.add(List.copyOf(columns));
			}
		}
		return List.copyOf(rows);
	}

	private static String stringValue(List<List<String>> rows, int row, int column, String fallback) {
		if (row >= rows.size() || column >= rows.get(row).size()) {
			return fallback;
		}
		String value = rows.get(row).get(column);
		return value == null || value.isBlank() ? fallback : value;
	}

	private static String stripComments(String source) {
		return source.replaceAll("(?m)%.*$", "").replaceAll("(?m)//.*$", "");
	}

	private static String caseName(String source) {
		Matcher matcher = CASE_NAME.matcher(source);
		return matcher.find() ? matcher.group(1) : "MATPOWER_Case";
	}

	private static double parseNumber(String token) {
		return Double.parseDouble(switch (token) {
			case "Inf", "+Inf" -> "Infinity";
			case "-Inf" -> "-Infinity";
			default -> token;
		});
	}

	private static int integer(double value, String label) {
		int integer = (int) value;
		if (integer != value) {
			throw new IllegalArgumentException("MATPOWER " + label + " must be an integer: " + value);
		}
		return integer;
	}

	private static void requireColumns(double[] row, int count, String section, int index) {
		if (row.length < count) {
			throw new IllegalArgumentException("MATPOWER " + section + " row " + index
					+ " requires at least " + count + " columns but found " + row.length);
		}
	}

	private static OpfBus requiredBus(Map<Integer, OpfBus> buses, int number, String owner) {
		OpfBus bus = buses.get(number);
		if (bus == null) {
			throw new IllegalArgumentException("MATPOWER " + owner + " references unknown bus " + number);
		}
		return bus;
	}

	private static String rawRecord(double[] row) {
		StringBuilder result = new StringBuilder();
		for (int index = 0; index < row.length; index++) {
			if (index > 0) {
				result.append(' ');
			}
			result.append(row[index]);
		}
		return result.toString();
	}
}
