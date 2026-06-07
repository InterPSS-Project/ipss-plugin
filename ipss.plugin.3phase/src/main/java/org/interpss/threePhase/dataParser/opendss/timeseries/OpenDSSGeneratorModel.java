package org.interpss.threePhase.dataParser.opendss.timeseries;

public class OpenDSSGeneratorModel {
	private final String id;
	private final String deviceClass;
	private final String busId;
	private final int phases;
	private final double kw;
	private final double kvar;
	private final double kva;
	private final double nominalKV;
	private final String connection;
	private final double powerFactor;
	private final double pmpp;
	private final double irradiance;
	private final double pctPmpp;
	private final double temperature;
	private final double pctCutIn;
	private final double pctCutOut;
	private final String efficiencyCurveId;
	private final String pvsTCurveId;
	private final String pctPmppCurveId;
	private final String kvarLimitCurveId;
	private final String storageState;
	private final double kwRated;
	private final double kwhRated;
	private final double kwhStored;
	private final double pctStored;
	private final double pctReserve;
	private final double pctCharge;
	private final double pctDischarge;
	private final double pctEffCharge;
	private final double pctEffDischarge;
	private final String dailyShapeId;
	private final String yearlyShapeId;
	private final String dutyShapeId;
	private final String sourceFile;
	private final int sourceLine;

	public OpenDSSGeneratorModel(String id, String deviceClass, String busId, int phases, double kw, double kvar,
			double kva, double pmpp, double irradiance, String dailyShapeId, String yearlyShapeId, String dutyShapeId,
			String sourceFile, int sourceLine) {
		this(id, deviceClass, busId, phases, kw, kvar, kva, 0.0, "", 0.0, pmpp, irradiance, 100.0, 25.0,
				0.0, 0.0, "", "", "", "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
				dailyShapeId, yearlyShapeId, dutyShapeId, sourceFile, sourceLine);
	}

	public OpenDSSGeneratorModel(String id, String deviceClass, String busId, int phases, double kw, double kvar,
			double kva, double nominalKV, String connection, double powerFactor, double pmpp, double irradiance,
			double pctPmpp, double temperature, double pctCutIn, double pctCutOut, String efficiencyCurveId,
			String pvsTCurveId, String pctPmppCurveId, String kvarLimitCurveId, String storageState,
			double kwRated, double kwhRated, double kwhStored, double pctStored, double pctReserve, double pctCharge,
			double pctDischarge, double pctEffCharge, double pctEffDischarge, String dailyShapeId,
			String yearlyShapeId, String dutyShapeId, String sourceFile, int sourceLine) {
		this.id = id;
		this.deviceClass = deviceClass;
		this.busId = busId;
		this.phases = phases;
		this.kw = kw;
		this.kvar = kvar;
		this.kva = kva;
		this.nominalKV = nominalKV;
		this.connection = connection == null ? "" : connection;
		this.powerFactor = powerFactor;
		this.pmpp = pmpp;
		this.irradiance = irradiance;
		this.pctPmpp = pctPmpp;
		this.temperature = temperature;
		this.pctCutIn = pctCutIn;
		this.pctCutOut = pctCutOut;
		this.efficiencyCurveId = efficiencyCurveId == null ? "" : efficiencyCurveId;
		this.pvsTCurveId = pvsTCurveId == null ? "" : pvsTCurveId;
		this.pctPmppCurveId = pctPmppCurveId == null ? "" : pctPmppCurveId;
		this.kvarLimitCurveId = kvarLimitCurveId == null ? "" : kvarLimitCurveId;
		this.storageState = storageState == null ? "" : storageState;
		this.kwRated = kwRated;
		this.kwhRated = kwhRated;
		this.kwhStored = kwhStored;
		this.pctStored = pctStored;
		this.pctReserve = pctReserve;
		this.pctCharge = pctCharge;
		this.pctDischarge = pctDischarge;
		this.pctEffCharge = pctEffCharge;
		this.pctEffDischarge = pctEffDischarge;
		this.dailyShapeId = dailyShapeId;
		this.yearlyShapeId = yearlyShapeId;
		this.dutyShapeId = dutyShapeId;
		this.sourceFile = sourceFile;
		this.sourceLine = sourceLine;
	}

	public String getId() {
		return id;
	}

	public String getDeviceClass() {
		return deviceClass;
	}

	public String getBusId() {
		return busId;
	}

	public int getPhases() {
		return phases;
	}

	public double getKw() {
		return kw;
	}

	public double getKvar() {
		return kvar;
	}

	public double getKva() {
		return kva;
	}

	public double getNominalKV() {
		return nominalKV;
	}

	public String getConnection() {
		return connection;
	}

	public double getPowerFactor() {
		return powerFactor;
	}

	public double getPmpp() {
		return pmpp;
	}

	public double getIrradiance() {
		return irradiance;
	}

	public double getPctPmpp() {
		return pctPmpp;
	}

	public double getTemperature() {
		return temperature;
	}

	public double getPctCutIn() {
		return pctCutIn;
	}

	public double getPctCutOut() {
		return pctCutOut;
	}

	public String getEfficiencyCurveId() {
		return efficiencyCurveId;
	}

	public String getPvsTCurveId() {
		return pvsTCurveId;
	}

	public String getPctPmppCurveId() {
		return pctPmppCurveId;
	}

	public String getKvarLimitCurveId() {
		return kvarLimitCurveId;
	}

	public String getStorageState() {
		return storageState;
	}

	public double getKwRated() {
		return kwRated;
	}

	public double getKwhRated() {
		return kwhRated;
	}

	public double getKwhStored() {
		return kwhStored;
	}

	public double getPctStored() {
		return pctStored;
	}

	public double getPctReserve() {
		return pctReserve;
	}

	public double getPctCharge() {
		return pctCharge;
	}

	public double getPctDischarge() {
		return pctDischarge;
	}

	public double getPctEffCharge() {
		return pctEffCharge;
	}

	public double getPctEffDischarge() {
		return pctEffDischarge;
	}

	public String getDailyShapeId() {
		return dailyShapeId;
	}

	public String getYearlyShapeId() {
		return yearlyShapeId;
	}

	public String getDutyShapeId() {
		return dutyShapeId;
	}

	public String getSourceFile() {
		return sourceFile;
	}

	public int getSourceLine() {
		return sourceLine;
	}
}
