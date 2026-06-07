package org.interpss.threePhase.dataParser.opendss.timeseries;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSParserDiagnostic.Severity;

public class OpenDSSLoadShapeParser {
	private final OpenDSSTimeSeriesData timeSeriesData;

	public OpenDSSLoadShapeParser(OpenDSSTimeSeriesData timeSeriesData) {
		this.timeSeriesData = timeSeriesData;
	}

	public boolean parseLoadShape(String logicalLine, String folderPath, String sourceFile, int sourceLine) {
		List<OpenDSSParserDiagnostic> diagnostics = new ArrayList<>();
		try {
			String trimmed = stripComment(logicalLine).trim();
			if(trimmed.isEmpty()) {
				return true;
			}
			String[] tokens = splitDssTokens(trimmed);
			String shapeId = loadShapeId(tokens);
			if(shapeId == null || shapeId.isEmpty()) {
				addDiagnostic(diagnostics, Severity.ERROR, "LoadShape id is missing", sourceFile, sourceLine);
				return addDiagnostics(diagnostics);
			}
			Map<String, String> properties = propertyMap(tokens);
			int npts = parseInteger(properties.get("npts"), 0);
			double intervalHours = parseIntervalHours(properties);
			double[] pMult = null;
			double[] qMult = null;
			double[] hour = parseArray(properties.get("hour"));
			String csvFile = firstPresent(properties, "csvfile", "file");
			if(csvFile != null) {
				CsvShape csvShape = readCsvShape(folderPath, csvFile, sourceFile, sourceLine, diagnostics);
				if(csvShape != null) {
					hour = csvShape.hour;
					pMult = csvShape.pMult;
					qMult = csvShape.qMult;
				}
			}
			if(pMult == null) {
				pMult = parseArray(firstPresent(properties, "pmult", "mult"));
			}
			if(qMult == null) {
				qMult = parseArray(properties.get("qmult"));
			}
			if(qMult.length == 0) {
				qMult = pMult.clone();
			}
			validateShape(shapeId, npts, hour, pMult, qMult, diagnostics, sourceFile, sourceLine);
			if(pMult.length > 0) {
				timeSeriesData.getShapeRegistry().add(new OpenDSSLoadShape(shapeId, npts, intervalHours, hour, pMult,
						qMult, sourceFile, sourceLine, diagnostics));
			}
			return addDiagnostics(diagnostics);
		} catch (Exception e) {
			addDiagnostic(diagnostics, Severity.ERROR, "Failed to parse LoadShape: " + e.getMessage(), sourceFile,
					sourceLine);
			return addDiagnostics(diagnostics);
		}
	}

	private boolean addDiagnostics(List<OpenDSSParserDiagnostic> diagnostics) {
		for(OpenDSSParserDiagnostic diagnostic : diagnostics) {
			timeSeriesData.addDiagnostic(diagnostic);
		}
		return true;
	}

	private static void validateShape(String shapeId, int npts, double[] hour, double[] pMult, double[] qMult,
			List<OpenDSSParserDiagnostic> diagnostics, String sourceFile, int sourceLine) {
		if(pMult.length == 0) {
			addDiagnostic(diagnostics, Severity.WARNING, "LoadShape " + shapeId + " has no multiplier data",
					sourceFile, sourceLine);
		}
		if(qMult.length != pMult.length) {
			addDiagnostic(diagnostics, Severity.WARNING, "LoadShape " + shapeId + " P/Q multiplier counts differ",
					sourceFile, sourceLine);
		}
		if(hour.length > 0 && hour.length != pMult.length) {
			addDiagnostic(diagnostics, Severity.WARNING, "LoadShape " + shapeId + " hour/multiplier counts differ",
					sourceFile, sourceLine);
		}
		if(npts > 0 && pMult.length > 0 && npts != pMult.length) {
			addDiagnostic(diagnostics, Severity.WARNING, "LoadShape " + shapeId + " npts does not match multiplier count",
					sourceFile, sourceLine);
		}
	}

	private static String loadShapeId(String[] tokens) {
		for(String token : tokens) {
			String normalized = token.toLowerCase(Locale.ROOT);
			if(normalized.startsWith("loadshape.")) {
				return unquote(token.substring("loadshape.".length()));
			}
			if(normalized.startsWith("object=loadshape.")) {
				return unquote(token.substring("object=loadshape.".length()));
			}
		}
		return null;
	}

	private static Map<String, String> propertyMap(String[] tokens) {
		Map<String, String> properties = new LinkedHashMap<>();
		for(String token : tokens) {
			int eq = token.indexOf('=');
			if(eq <= 0) {
				continue;
			}
			String key = token.substring(0, eq).trim().toLowerCase(Locale.ROOT);
			String value = stripTrailingComma(token.substring(eq + 1).trim());
			properties.put(key, unquote(value));
		}
		return properties;
	}

	private static String firstPresent(Map<String, String> properties, String first, String second) {
		String value = properties.get(first);
		if(value == null || value.isEmpty()) {
			value = properties.get(second);
		}
		return value;
	}

	private static double parseIntervalHours(Map<String, String> properties) {
		if(properties.containsKey("sinterval")) {
			return parseDouble(properties.get("sinterval"), 0.0) / 3600.0;
		}
		if(properties.containsKey("minterval")) {
			return parseDouble(properties.get("minterval"), 0.0) / 60.0;
		}
		return parseDouble(properties.get("interval"), 1.0);
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
		String normalized = value.trim();
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

	private static CsvShape readCsvShape(String folderPath, String csvFile, String sourceFile, int sourceLine,
			List<OpenDSSParserDiagnostic> diagnostics) throws IOException {
		File file = new File(unquote(csvFile));
		if(!file.isAbsolute()) {
			file = new File(folderPath == null ? "" : folderPath, unquote(csvFile));
		}
		if(!file.exists()) {
			addDiagnostic(diagnostics, Severity.WARNING, "LoadShape csv file not found: " + file.getPath(), sourceFile,
					sourceLine);
			return null;
		}
		List<Double> hour = new ArrayList<>();
		List<Double> pMult = new ArrayList<>();
		List<Double> qMult = new ArrayList<>();
		try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line = reader.readLine();
			while(line != null) {
				String data = stripComment(line).trim();
				if(!data.isEmpty()) {
					String[] values = data.split("[,\\s]+");
					if(values.length == 1) {
						pMult.add(Double.valueOf(values[0]));
					}
					else if(values.length == 2) {
						hour.add(Double.valueOf(values[0]));
						pMult.add(Double.valueOf(values[1]));
					}
					else {
						hour.add(Double.valueOf(values[0]));
						pMult.add(Double.valueOf(values[1]));
						qMult.add(Double.valueOf(values[2]));
					}
				}
				line = reader.readLine();
			}
		}
		return new CsvShape(toArray(hour), toArray(pMult), qMult.isEmpty() ? null : toArray(qMult));
	}

	private static double[] toArray(List<Double> values) {
		double[] array = new double[values.size()];
		for(int i = 0; i < values.size(); i++) {
			array[i] = values.get(i);
		}
		return array;
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
		int index = text.indexOf('!');
		return index >= 0 ? text.substring(0, index) : text;
	}

	private static String stripTrailingComma(String value) {
		String result = value;
		while(result.endsWith(",")) {
			result = result.substring(0, result.length() - 1).trim();
		}
		return result;
	}

	private static String unquote(String value) {
		String trimmed = stripTrailingComma(value.trim());
		if((trimmed.startsWith("\"") && trimmed.endsWith("\""))
				|| (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
			return trimmed.substring(1, trimmed.length() - 1);
		}
		return trimmed;
	}

	private static void addDiagnostic(List<OpenDSSParserDiagnostic> diagnostics, Severity severity, String message,
			String sourceFile, int sourceLine) {
		diagnostics.add(new OpenDSSParserDiagnostic(severity, message, sourceFile, sourceLine));
	}

	private static final class CsvShape {
		private final double[] hour;
		private final double[] pMult;
		private final double[] qMult;

		private CsvShape(double[] hour, double[] pMult, double[] qMult) {
			this.hour = hour;
			this.pMult = pMult;
			this.qMult = qMult;
		}
	}
}
