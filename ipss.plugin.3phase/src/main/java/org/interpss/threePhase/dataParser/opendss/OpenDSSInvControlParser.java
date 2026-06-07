package org.interpss.threePhase.dataParser.opendss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.interpss.threePhase.powerflow.control.InverterControlData;
import org.interpss.threePhase.powerflow.control.InverterControlData.ControlMode;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSGeneratorModel;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSParserDiagnostic;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSParserDiagnostic.Severity;
import org.interpss.threePhase.qsts.InverterGenAdapter;
import org.interpss.threePhase.qsts.InverterCapabilityData;
import org.interpss.threePhase.qsts.QstsControlCurve;

public class OpenDSSInvControlParser {
	private final OpenDSSDataParser dataParser;

	public OpenDSSInvControlParser(OpenDSSDataParser dataParser) {
		this.dataParser = dataParser;
	}

	public boolean parseInvControlData(String invControlStr) {
		String[] tokens = splitDssTokens(invControlStr.trim().replaceAll("\\s*=\\s*", "="));
		Map<String, String> properties = propertyMap(tokens);
		String id = deviceId(tokens);
		String generatorId = firstDerId(stripDssValue(firstPresent(properties, "derlist", "pvssystemlist")));
		ControlMode mode = parseMode(firstPresent(properties, "mode", "controlmode"));
		String curveId = curveIdForMode(properties, mode);
		double ratedKva = parseDouble(firstPresent(properties, "kva", "ratedkva"), 0.0);
		double minQ = -parseDouble(firstPresent(properties, "kvarmaxabs", "kvarmax"), Double.NaN);
		double maxQ = parseDouble(firstPresent(properties, "kvarmax", "kvarmaxabs"), Double.NaN);
		if(properties.containsKey("kvarmin")) {
			minQ = parseDouble(properties.get("kvarmin"), minQ);
		}
		double minPf = parseDouble(firstPresent(properties, "pfmin", "minpf"), 0.0);
		if(generatorId.isEmpty()) {
			dataParser.getTimeSeriesData().addDiagnostic(new OpenDSSParserDiagnostic(Severity.WARNING,
					"InvControl " + id + " missing DERList; control metadata ignored", null, -1));
			return true;
		}
		String effectiveCurveId = transformCurve(id, generatorId, curveId, mode, minQ, maxQ);
		dataParser.getTimeSeriesData().addInverterControl(new InverterControlData(id, generatorId,
				mode, effectiveCurveId, ratedKva, minQ, maxQ, minPf, true));
		return true;
	}

	private String transformCurve(String controlId, String generatorId, String curveId, ControlMode mode,
			double minQ, double maxQ) {
		QstsControlCurve curve = dataParser.getTimeSeriesData().getControlCurve(curveId);
		if(curve == null) {
			return curveId;
		}
		OpenDSSGeneratorModel model = dataParser.getTimeSeriesData().getGeneratorModel(generatorId);
		if(model == null) {
			return curveId;
		}
		double[] x = curve.getX();
		double[] y = curve.getY();
		if(mode == ControlMode.WATTPF) {
			scale(x, activePowerBaseKw(model));
		}
		else if(mode == ControlMode.WATTVAR) {
			scale(x, activePowerBaseKw(model));
			scale(y, reactivePowerBaseKvar(generatorId, model, minQ, maxQ));
		}
		else if(mode == ControlMode.VOLTWATT) {
			scale(y, activePowerBaseKw(model));
		}
		else {
			scale(y, reactivePowerBaseKvar(generatorId, model, minQ, maxQ));
		}
		String transformedId = controlId + "_" + curveId + "_" + mode.name().toLowerCase(Locale.ROOT);
		dataParser.getTimeSeriesData().addControlCurve(new QstsControlCurve(transformedId, x, y));
		return transformedId;
	}

	private double activePowerBaseKw(OpenDSSGeneratorModel model) {
		if(model.getPmpp() > 0.0) {
			return model.getPmpp();
		}
		if(model.getKwRated() > 0.0) {
			return model.getKwRated();
		}
		return Math.abs(model.getKw());
	}

	private double reactivePowerBaseKvar(String generatorId, OpenDSSGeneratorModel model, double minQ, double maxQ) {
		double propertyBase = maxFiniteAbs(minQ, maxQ);
		if(Double.isFinite(propertyBase) && propertyBase > 0.0) {
			return propertyBase;
		}
		InverterGenAdapter adapter = dataParser.getTimeSeriesData().getInverterAdapterStore().get(generatorId);
		if(adapter != null) {
			InverterCapabilityData capability = adapter.getCapabilityData();
			double capabilityBase = maxFiniteAbs(capability.getMinReactivePowerKvar(),
					capability.getMaxReactivePowerKvar());
			if(Double.isFinite(capabilityBase) && capabilityBase > 0.0) {
				return capabilityBase;
			}
		}
		if(model.getKva() > 0.0) {
			double p = Math.abs(model.getKw());
			return Math.sqrt(Math.max(0.0, model.getKva() * model.getKva() - p * p));
		}
		return 1.0;
	}

	private static double maxFiniteAbs(double first, double second) {
		double max = Double.NaN;
		if(Double.isFinite(first)) {
			max = Math.abs(first);
		}
		if(Double.isFinite(second)) {
			double value = Math.abs(second);
			max = Double.isFinite(max) ? Math.max(max, value) : value;
		}
		return max;
	}

	private static void scale(double[] values, double base) {
		if(!Double.isFinite(base) || base == 0.0) {
			return;
		}
		for(int i = 0; i < values.length; i++) {
			values[i] *= base;
		}
	}

	private static ControlMode parseMode(String value) {
		String normalized = stripDssValue(value).replace("_", "").toUpperCase(Locale.ROOT);
		if(normalized.equals("VOLTWATT")) {
			return ControlMode.VOLTWATT;
		}
		if(normalized.equals("WATTPF")) {
			return ControlMode.WATTPF;
		}
		if(normalized.equals("WATTVAR")) {
			return ControlMode.WATTVAR;
		}
		return ControlMode.VOLTVAR;
	}

	private static String curveIdForMode(Map<String, String> properties, ControlMode mode) {
		if(mode == ControlMode.VOLTWATT) {
			return stripDssValue(firstPresent(properties, "voltwatt_curve", "voltwattcurve"));
		}
		if(mode == ControlMode.WATTPF) {
			return stripDssValue(firstPresent(properties, "wattpf_curve", "wattpfcurve"));
		}
		if(mode == ControlMode.WATTVAR) {
			return stripDssValue(firstPresent(properties, "wattvar_curve", "wattvarcurve"));
		}
		return stripDssValue(firstPresent(properties, "vvc_curve1", "voltage_curvex_ref"));
	}

	private static String firstDerId(String derList) {
		String normalized = derList.replace("(", "").replace(")", "")
				.replace("[", "").replace("]", "").trim();
		if(normalized.isEmpty()) {
			return "";
		}
		String first = normalized.split("[,\\s]+")[0];
		int dot = first.indexOf('.');
		return dot >= 0 ? first.substring(dot + 1) : first;
	}

	private static String deviceId(String[] tokens) {
		for(String token : tokens) {
			String lower = token.toLowerCase(Locale.ROOT);
			if(lower.startsWith("invcontrol.")) {
				return stripDssValue(token.substring("invcontrol.".length()));
			}
		}
		return "";
	}

	private static Map<String, String> propertyMap(String[] tokens) {
		Map<String, String> properties = new LinkedHashMap<String, String>();
		for(String token : tokens) {
			int eq = token.indexOf('=');
			if(eq > 0) {
				properties.put(token.substring(0, eq).trim().toLowerCase(Locale.ROOT),
						stripDssValue(token.substring(eq + 1)));
			}
		}
		return properties;
	}

	private static String firstPresent(Map<String, String> properties, String first, String second) {
		String value = properties.get(first);
		return value == null || value.isEmpty() ? properties.get(second) : value;
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
		List<String> tokens = new ArrayList<String>();
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
