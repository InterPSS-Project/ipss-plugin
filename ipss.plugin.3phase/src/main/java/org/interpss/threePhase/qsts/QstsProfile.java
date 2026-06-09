package org.interpss.threePhase.qsts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QstsProfile {
	private final String id;
	private final double[] hour;
	private final double[] pMult;
	private final double[] qMult;
	private final List<String> diagnostics;

	public QstsProfile(String id, double[] hour, double[] pMult, double[] qMult, List<String> diagnostics) {
		if(id == null || id.trim().isEmpty()) {
			throw new IllegalArgumentException("QSTS profile id is required");
		}
		if(pMult == null || pMult.length == 0) {
			throw new IllegalArgumentException("QSTS profile " + id + " requires at least one P multiplier");
		}
		this.id = id;
		this.hour = hour == null ? new double[0] : hour.clone();
		this.pMult = pMult.clone();
		this.qMult = qMult == null ? pMult.clone() : qMult.clone();
		this.diagnostics = diagnostics == null
				? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(diagnostics));
		if(this.qMult.length != this.pMult.length) {
			throw new IllegalArgumentException("QSTS profile " + id + " P/Q multiplier counts differ");
		}
		if(this.hour.length > 0 && this.hour.length != this.pMult.length) {
			throw new IllegalArgumentException("QSTS profile " + id + " hour/multiplier counts differ");
		}
	}

	public String getId() {
		return id;
	}

	public int getPointCount() {
		return pMult.length;
	}

	public double[] getHour() {
		return hour.clone();
	}

	public double[] getPMult() {
		return pMult.clone();
	}

	public double[] getQMult() {
		return qMult.clone();
	}

	public double getPMultiplierAtIndex(int index) {
		return pMult[index];
	}

	public double getQMultiplierAtIndex(int index) {
		return qMult[index];
	}

	public List<String> getDiagnostics() {
		return diagnostics;
	}
}
