package org.interpss.plugin.opf.util;

public record MatpowerDcLineData(int index, double fromPmw, double toPmw,
		double fromQmvar, double toQmvar, String rawRecord) {
}
