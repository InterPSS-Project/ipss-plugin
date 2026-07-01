package org.interpss.threePhase.dataParser.opendss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.interpss.threePhase.qsts.QstsControlCurve;

public class OpenDSSXYCurveParser {
	private final OpenDSSDataParser dataParser;

	public OpenDSSXYCurveParser(OpenDSSDataParser dataParser) {
		this.dataParser = dataParser;
	}

	public boolean parseXYCurve(String xyCurveStr) {
		String[] tokens = splitDssTokens(xyCurveStr.trim().replaceAll("\\s*=\\s*", "="));
		String id = curveId(tokens);
		if(id == null || id.isEmpty()) {
			return true;
		}
		Map<String, String> properties = propertyMap(tokens);
		double[] x = parseArray(firstPresent(properties, "xarray", "x"));
		double[] y = parseArray(firstPresent(properties, "yarray", "y"));
		if(x.length > 0 && x.length == y.length) {
			dataParser.getTimeSeriesData().addControlCurve(new QstsControlCurve(id, x, y));
		}
		return true;
	}

	private static String curveId(String[] tokens) {
		for(String token : tokens) {
			String lower = token.toLowerCase(Locale.ROOT);
			if(lower.startsWith("xycurve.")) {
				return stripDssValue(token.substring("xycurve.".length()));
			}
		}
		return "";
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

	private static String firstPresent(Map<String, String> properties, String first, String second) {
		String value = properties.get(first);
		return value == null || value.isEmpty() ? properties.get(second) : value;
	}

	private static double[] parseArray(String value) {
		if(value == null || value.trim().isEmpty()) {
			return new double[0];
		}
		String normalized = stripDssValue(value);
		if((normalized.startsWith("(") && normalized.endsWith(")"))
				|| (normalized.startsWith("[") && normalized.endsWith("]"))) {
			normalized = normalized.substring(1, normalized.length() - 1).trim();
		}
		if(normalized.isEmpty()) {
			return new double[0];
		}
		String[] parts = normalized.split("[,\\s]+");
		double[] values = new double[parts.length];
		for(int i = 0; i < parts.length; i++) {
			values[i] = Double.valueOf(stripDssValue(parts[i]));
		}
		return values;
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
