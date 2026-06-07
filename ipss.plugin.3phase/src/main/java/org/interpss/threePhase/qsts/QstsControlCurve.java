package org.interpss.threePhase.qsts;

import java.util.Arrays;

public class QstsControlCurve {
	private final String id;
	private final double[] x;
	private final double[] y;

	public QstsControlCurve(String id, double[] x, double[] y) {
		if(x == null || y == null || x.length == 0 || x.length != y.length) {
			throw new IllegalArgumentException("QSTS control curve requires equal non-empty x/y arrays");
		}
		this.id = id == null ? "" : id;
		this.x = Arrays.copyOf(x, x.length);
		this.y = Arrays.copyOf(y, y.length);
	}

	public String getId() {
		return id;
	}

	public double evaluate(double value) {
		if(value <= x[0]) {
			return y[0];
		}
		for(int i = 1; i < x.length; i++) {
			if(value <= x[i]) {
				double dx = x[i] - x[i - 1];
				if(Math.abs(dx) <= 1.0e-12) {
					return y[i];
				}
				double fraction = (value - x[i - 1]) / dx;
				return y[i - 1] + fraction * (y[i] - y[i - 1]);
			}
		}
		return y[y.length - 1];
	}
}
