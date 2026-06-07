package org.interpss.threePhase.qsts;

public class QstsLoadMultiplier {
	private final double pMultiplier;
	private final double qMultiplier;
	private final String profileId;

	public QstsLoadMultiplier(double pMultiplier, double qMultiplier, String profileId) {
		this.pMultiplier = pMultiplier;
		this.qMultiplier = qMultiplier;
		this.profileId = profileId;
	}

	public double getPMultiplier() {
		return pMultiplier;
	}

	public double getQMultiplier() {
		return qMultiplier;
	}

	public String getProfileId() {
		return profileId;
	}
}
