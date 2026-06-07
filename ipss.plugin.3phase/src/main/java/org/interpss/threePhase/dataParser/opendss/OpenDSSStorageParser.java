package org.interpss.threePhase.dataParser.opendss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSGeneratorModel;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileBinding;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileType;
import org.interpss.threePhase.qsts.QstsDeviceStatus;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.IPhaseGen;
import com.interpss.core.threephase.Static3PBus;
import com.interpss.core.threephase.Static3PGen;
import com.interpss.dstab.GeneratorType;

public class OpenDSSStorageParser {
	private final OpenDSSDataParser dataParser;

	public OpenDSSStorageParser(OpenDSSDataParser dataParser) {
		this.dataParser = dataParser;
	}

	public boolean parseStorageData(String storageStr, String sourceFile, int sourceLine) {
		String[] tokens = splitDssTokens(storageStr.toLowerCase(Locale.ROOT).trim().replaceAll("\\s*=\\s*", "="));
		Map<String, String> properties = propertyMap(tokens);
		String id = deviceId(tokens, "storage");
		if(id == null || id.isEmpty()) {
			return true;
		}
		String busIdPhases = firstPresent(properties, "bus1", "bus");
		if(busIdPhases == null || busIdPhases.isEmpty()) {
			return true;
		}
		int phases = parseInt(properties.get("phases"), 3);
		String busId = dataParser.getBusIdPrefix() + baseBusId(busIdPhases);
		double nominalKV = parseDouble(properties.get("kv"), 0.0);
		String connection = stripDssValue(firstPresent(properties, "conn", "connection"));
		double kva = parseDouble(properties.get("kva"), 0.0);
		double kwRated = parseDouble(properties.get("kwrated"), parseDouble(properties.get("kw"), 0.0));
		double kw = parseDouble(properties.get("kw"), kwRated);
		double kvar = parseKvar(properties, kw, kva);
		double powerFactor = parseDouble(properties.get("pf"), 0.0);
		String storageState = stripDssValue(properties.get("state")).toLowerCase(Locale.ROOT);
		double signedKw = storageState.equals("charging") ? -Math.abs(kw)
				: storageState.equals("idling") ? 0.0 : kw;
		double kwhRated = parseDouble(properties.get("kwhrated"), 0.0);
		double kwhStored = parseDouble(properties.get("kwhstored"), 0.0);
		double pctStored = parseDouble(properties.get("%stored"), parseDouble(properties.get("pctstored"), 0.0));
		double pctReserve = parseDouble(properties.get("%reserve"), parseDouble(properties.get("pctreserve"), 0.0));
		double pctCharge = parseDouble(properties.get("%charge"), parseDouble(properties.get("pctcharge"), 0.0));
		double pctDischarge = parseDouble(properties.get("%discharge"), parseDouble(properties.get("pctdischarge"), 0.0));
		double pctEffCharge = parseDouble(properties.get("%effcharge"), parseDouble(properties.get("pcteffcharge"), 0.0));
		double pctEffDischarge = parseDouble(properties.get("%effdischarge"),
				parseDouble(properties.get("pcteffdischarge"), 0.0));
		String daily = stripDssValue(properties.get("daily"));
		String yearly = stripDssValue(properties.get("yearly"));
		String duty = stripDssValue(properties.get("duty"));
		String status = stripDssValue(properties.get("status"));

		IPhaseGen generator;
		Complex genPu = new Complex(signedKw / dataParser.getNetworkBaseKva(),
				kvar / dataParser.getNetworkBaseKva());
		if(dataParser.isStaticNetworkMode()) {
			Static3PBus bus = dataParser.getOrCreateStaticBus(busId);
			Static3PGen staticGenerator = ThreePhaseObjectFactory.createStatic3PGenerator(id);
			staticGenerator.setMvaBase(dataParser.getNetworkBaseMva());
			staticGenerator.setGen(genPu);
			staticGenerator.setPower3Phase(generatorPhasePower(genPu, phases, phaseCode(busIdPhases)), UnitType.PU);
			bus.getContributeGenList().add(staticGenerator);
			if(bus.getGenCode() == AclfGenCode.NON_GEN) {
				bus.setGenCode(AclfGenCode.GEN_PQ);
			}
			generator = staticGenerator;
		}
		else {
			DStab3PBus bus = dataParser.getDistNetwork().getBus(busId);
			if(bus == null) {
				bus = ThreePhaseObjectFactory.create3PDStabBus(busId, dataParser.getDistNetwork());
			}
			DStab3PGen dstabGenerator = ThreePhaseObjectFactory.create3PGenerator(id);
			dstabGenerator.setGenType(GeneratorType.INVERTER_BASED);
			dstabGenerator.setMvaBase(dataParser.getNetworkBaseMva());
			dstabGenerator.setGen(genPu);
			dstabGenerator.setPower3Phase(generatorPhasePower(genPu, phases, phaseCode(busIdPhases)), UnitType.PU);
			bus.getContributeGenList().add(dstabGenerator);
			if(bus.getGenCode() == AclfGenCode.NON_GEN) {
				bus.setGenCode(AclfGenCode.GEN_PQ);
			}
			generator = dstabGenerator;
		}

		dataParser.getTimeSeriesData().getGeneratorStateStore().register(generator);
		OpenDSSProfileBinding binding = dataParser.getTimeSeriesData().getOrCreateGeneratorBinding(id);
		binding.setShapeId(OpenDSSProfileType.DAILY, daily);
		binding.setShapeId(OpenDSSProfileType.YEARLY, yearly);
		binding.setShapeId(OpenDSSProfileType.DUTY, duty);
		binding.setStatus(parseStatus(status));
		dataParser.getTimeSeriesData().addGeneratorModel(new OpenDSSGeneratorModel(id, "storage", busId, phases,
				signedKw, kvar, kva, nominalKV, connection, powerFactor, 0.0, 1.0, 100.0, 25.0,
				0.0, 0.0, "", "", "", "", storageState, kwRated, kwhRated, kwhStored, pctStored, pctReserve,
				pctCharge, pctDischarge, pctEffCharge, pctEffDischarge, daily, yearly, duty, sourceFile, sourceLine));
		return true;
	}

	private static double parseKvar(Map<String, String> properties, double kw, double kva) {
		if(properties.containsKey("kvar")) {
			return parseDouble(properties.get("kvar"), 0.0);
		}
		double pf = parseDouble(properties.get("pf"), 0.0);
		if(pf != 0.0) {
			double q = Math.abs(kw) * Math.tan(Math.acos(Math.min(1.0, Math.abs(pf))));
			return pf < 0.0 ? -q : q;
		}
		if(kva > 0.0 && kva >= Math.abs(kw)) {
			return Math.sqrt(Math.max(0.0, kva * kva - kw * kw));
		}
		return 0.0;
	}

	private static QstsDeviceStatus parseStatus(String status) {
		if(status == null || status.trim().isEmpty()) {
			return QstsDeviceStatus.DEFAULT;
		}
		String normalized = stripDssValue(status).toLowerCase(Locale.ROOT);
		if(normalized.equals("fixed")) {
			return QstsDeviceStatus.FIXED;
		}
		if(normalized.equals("variable")) {
			return QstsDeviceStatus.VARIABLE;
		}
		if(normalized.equals("exempt")) {
			return QstsDeviceStatus.EXEMPT;
		}
		return QstsDeviceStatus.DEFAULT;
	}

	private static String deviceId(String[] tokens, String deviceClass) {
		String prefix = deviceClass + ".";
		for(String token : tokens) {
			if(token.startsWith(prefix)) {
				return stripDssValue(token.substring(prefix.length()));
			}
		}
		return null;
	}

	private static String baseBusId(String busIdPhases) {
		String value = stripDssValue(busIdPhases);
		int dot = value.indexOf('.');
		return dot >= 0 ? value.substring(0, dot) : value;
	}

	private static PhaseCode phaseCode(String busIdPhases) {
		String value = stripDssValue(busIdPhases);
		String[] parts = value.split("\\.");
		if(parts.length < 2) {
			return PhaseCode.ABC;
		}
		if("2".equals(parts[1])) {
			return PhaseCode.B;
		}
		if("3".equals(parts[1])) {
			return PhaseCode.C;
		}
		return PhaseCode.A;
	}

	private static Complex3x1 generatorPhasePower(Complex genPu, int phases, PhaseCode phaseCode) {
		if(phases == 1) {
			if(phaseCode == PhaseCode.B) {
				return new Complex3x1(Complex.ZERO, genPu, Complex.ZERO);
			}
			if(phaseCode == PhaseCode.C) {
				return new Complex3x1(Complex.ZERO, Complex.ZERO, genPu);
			}
			return new Complex3x1(genPu, Complex.ZERO, Complex.ZERO);
		}
		Complex phasePower = genPu.divide(3.0);
		return new Complex3x1(phasePower, phasePower, phasePower);
	}

	private static String firstPresent(Map<String, String> properties, String first, String second) {
		String value = properties.get(first);
		return value == null || value.isEmpty() ? properties.get(second) : value;
	}

	private static Map<String, String> propertyMap(String[] tokens) {
		Map<String, String> properties = new LinkedHashMap<>();
		for(String token : tokens) {
			int eq = token.indexOf('=');
			if(eq > 0) {
				properties.put(token.substring(0, eq).trim().toLowerCase(Locale.ROOT),
						stripDssValue(token.substring(eq + 1)));
			}
		}
		return properties;
	}

	private static int parseInt(String value, int defaultValue) {
		if(value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		return Integer.valueOf(stripDssValue(value));
	}

	private static double parseDouble(String value, double defaultValue) {
		if(value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		return Double.valueOf(stripDssValue(value));
	}

	private static String stripDssValue(String value) {
		String normalized = value == null ? "" : value.trim();
		while(normalized.endsWith(",")) {
			normalized = normalized.substring(0, normalized.length() - 1).trim();
		}
		if((normalized.startsWith("\"") && normalized.endsWith("\""))
				|| (normalized.startsWith("'") && normalized.endsWith("'"))) {
			normalized = normalized.substring(1, normalized.length() - 1);
		}
		return normalized;
	}

	private static String[] splitDssTokens(String text) {
		List<String> tokens = new ArrayList<>();
		StringBuilder token = new StringBuilder();
		int bracketDepth = 0;
		for(int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if(ch == '(' || ch == '[') {
				bracketDepth++;
			}
			else if(ch == ')' || ch == ']') {
				bracketDepth = Math.max(0, bracketDepth - 1);
			}
			if(Character.isWhitespace(ch) && bracketDepth == 0) {
				if(token.length() > 0) {
					tokens.add(token.toString());
					token.setLength(0);
				}
			}
			else {
				token.append(ch);
			}
		}
		if(token.length() > 0) {
			tokens.add(token.toString());
		}
		return tokens.toArray(new String[0]);
	}
}
