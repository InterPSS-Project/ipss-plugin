package org.interpss.threePhase.qsts;

import java.util.Locale;

public class QstsCsvExporter {
	public String exportBusVoltages(QstsResult result) {
		StringBuilder builder = new StringBuilder("step,hour,bus,phase,vmag_pu,angle_deg\n");
		if(result == null) {
			return builder.toString();
		}
		for(QstsBusVoltageSample sample : result.getBusVoltages()) {
			builder.append(sample.getStepIndex()).append(',')
					.append(format(sample.getHour())).append(',')
					.append(escape(sample.getBusId())).append(',')
					.append(sample.getPhase()).append(',')
					.append(format(sample.getMagnitude())).append(',')
					.append(format(sample.getAngleDegrees())).append('\n');
		}
		return builder.toString();
	}

	public String exportLoadPowers(QstsResult result) {
		return exportDevicePowers(result == null ? null : result.getLoadPowers());
	}

	public String exportGeneratorPowers(QstsResult result) {
		return exportDevicePowers(result == null ? null : result.getGeneratorPowers());
	}

	public String exportDevicePowers(Iterable<QstsDevicePowerSample> samples) {
		StringBuilder builder = new StringBuilder("step,hour,device_class,device,phase,p_pu,q_pu\n");
		if(samples == null) {
			return builder.toString();
		}
		for(QstsDevicePowerSample sample : samples) {
			builder.append(sample.getStepIndex()).append(',')
					.append(format(sample.getHour())).append(',')
					.append(escape(sample.getDeviceClass())).append(',')
					.append(escape(sample.getDeviceId())).append(',')
					.append(sample.getPhase()).append(',')
					.append(format(sample.getP())).append(',')
					.append(format(sample.getQ())).append('\n');
		}
		return builder.toString();
	}

	public String exportCapacitorStates(Iterable<QstsCapacitorStateSample> states) {
		StringBuilder builder = new StringBuilder(
				"step,hour,capacitor,closed,total_q_pu,total_q_kvar,operation_count\n");
		if(states == null) {
			return builder.toString();
		}
		for(QstsCapacitorStateSample state : states) {
			builder.append(state.getStepIndex()).append(',')
					.append(format(state.getHour())).append(',')
					.append(escape(state.getCapacitorId())).append(',')
					.append(state.isClosed()).append(',')
					.append(format(state.getTotalReactivePowerPu())).append(',')
					.append(format(state.getTotalReactivePowerKvar())).append(',')
					.append(state.getOperationCount()).append('\n');
		}
		return builder.toString();
	}

	public String exportCapacitorStates(QstsResult result) {
		return exportCapacitorStates(result == null ? null : result.getCapacitorStates());
	}

	public String exportInverterControls(Iterable<QstsInverterControlSample> samples) {
		StringBuilder builder = new StringBuilder(
				"step,hour,control,generator,mode,applied,p_kw,q_kvar,limited,reason\n");
		if(samples == null) {
			return builder.toString();
		}
		for(QstsInverterControlSample sample : samples) {
			builder.append(sample.getStepIndex()).append(',')
					.append(format(sample.getHour())).append(',')
					.append(escape(sample.getControlId())).append(',')
					.append(escape(sample.getGeneratorId())).append(',')
					.append(escape(sample.getMode())).append(',')
					.append(sample.isApplied()).append(',')
					.append(format(sample.getActivePowerKw())).append(',')
					.append(format(sample.getReactivePowerKvar())).append(',')
					.append(sample.isLimited()).append(',')
					.append(escape(sample.getReason())).append('\n');
		}
		return builder.toString();
	}

	public String exportInverterControls(QstsResult result) {
		return exportInverterControls(result == null ? null : result.getInverterControls());
	}

	private static String format(double value) {
		if(!Double.isFinite(value)) {
			return "";
		}
		return String.format(Locale.ROOT, "%.12g", value);
	}

	private static String escape(String value) {
		if(value == null) {
			return "";
		}
		if(value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
}
