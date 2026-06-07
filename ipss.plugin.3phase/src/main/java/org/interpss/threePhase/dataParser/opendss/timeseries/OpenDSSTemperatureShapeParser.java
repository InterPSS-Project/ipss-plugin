package org.interpss.threePhase.dataParser.opendss.timeseries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OpenDSSTemperatureShapeParser {
	private final OpenDSSTimeSeriesData timeSeriesData;

	public OpenDSSTemperatureShapeParser(OpenDSSTimeSeriesData timeSeriesData) {
		this.timeSeriesData = timeSeriesData;
	}

	public boolean parseTemperatureShape(String logicalLine, String sourceFile, int sourceLine) {
		String trimmed = stripComment(logicalLine).trim();
		if(trimmed.isEmpty()) {
			return true;
		}
		String[] tokens = splitDssTokens(trimmed.replaceAll("\\s*=\\s*", "="));
		String shapeId = shapeId(tokens);
		if(shapeId == null || shapeId.isEmpty()) {
			return true;
		}
		Map<String, String> properties = propertyMap(tokens);
		double[] temperature = parseArray(firstPresent(properties, "temp", "temperature"));
		if(temperature.length > 0) {
			timeSeriesData.addTemperatureShape(new OpenDSSTemperatureShape(shapeId,
					parseInteger(properties.get("npts"), 0),
					parseDouble(properties.get("interval"), 1.0),
					parseArray(properties.get("hour")), temperature, sourceFile, sourceLine));
		}
		return true;
	}

	private static String shapeId(String[] tokens) {
		for(String token : tokens) {
			String normalized = token.toLowerCase(Locale.ROOT);
			if(normalized.startsWith("tshape.")) {
				return unquote(token.substring("tshape.".length()));
			}
		}
		return null;
	}

	private static Map<String, String> propertyMap(String[] tokens) {
		Map<String, String> properties = new LinkedHashMap<>();
		for(String token : tokens) {
			int eq = token.indexOf('=');
			if(eq > 0) {
				properties.put(token.substring(0, eq).trim().toLowerCase(Locale.ROOT),
						unquote(token.substring(eq + 1)));
			}
		}
		return properties;
	}

	private static String firstPresent(Map<String, String> properties, String first, String second) {
		String value = properties.get(first);
		return value == null || value.isEmpty() ? properties.get(second) : value;
	}

	private static int parseInteger(String value, int defaultValue) {
		if(value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		return Integer.valueOf(stripTrailingComma(value.trim()));
	}

	private static double parseDouble(String value, double defaultValue) {
		if(value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		return Double.valueOf(stripTrailingComma(value.trim()));
	}

	private static double[] parseArray(String value) {
		if(value == null || value.trim().isEmpty()) {
			return new double[0];
		}
		String normalized = unquote(value);
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
			values[i] = parseDouble(parts[i], 0.0);
		}
		return values;
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

	private static String stripComment(String text) {
		int index = text == null ? -1 : text.indexOf('!');
		return index >= 0 ? text.substring(0, index) : text == null ? "" : text;
	}

	private static String stripTrailingComma(String value) {
		String result = value;
		while(result.endsWith(",")) {
			result = result.substring(0, result.length() - 1).trim();
		}
		return result;
	}

	private static String unquote(String value) {
		String trimmed = stripTrailingComma(value == null ? "" : value.trim());
		if((trimmed.startsWith("\"") && trimmed.endsWith("\""))
				|| (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
			return trimmed.substring(1, trimmed.length() - 1);
		}
		return trimmed;
	}
}
