package org.interpss.threePhase.opf.dist.constraint;

import java.util.ArrayList;
import java.util.List;

import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.model.DistOpfBatteryData;
import org.interpss.threePhase.opf.dist.model.DistOpfDerData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

import com.interpss.core.acsc.PhaseCode;

public class DistBatteryStateOfChargeConstraintCollector extends BaseDistOpfConstraintCollector {

	private final DistOpfOptions options;

	public DistBatteryStateOfChargeConstraintCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, List<OpfConstraint> constraints,
			DistOpfOptions options) {
		super(modelData, variableIndex, constraints);
		this.options = options;
	}

	@Override
	public void collectConstraint() {
		for (DistOpfDerData der : modelData.getDers()) {
			if (der instanceof DistOpfBatteryData) {
				addStateOfChargeConstraint((DistOpfBatteryData) der);
			}
		}
	}

	private void addStateOfChargeConstraint(DistOpfBatteryData battery) {
		if (!battery.hasStateOfChargeLimits() || options.getTimeStepHours() <= 0.0) {
			return;
		}
		List<Integer> columns = new ArrayList<Integer>();
		List<Double> values = new ArrayList<Double>();
		for (PhaseCode phase : battery.getPhases()) {
			columns.add(variableIndex.derP(battery.getId(), phase));
			values.add(1.0);
		}

		double energy = battery.getEnergyCapacityPuHour();
		double stepHours = options.getTimeStepHours();
		double lowerP = (battery.getInitialStateOfChargePu() - battery.getMaxStateOfChargePu())
				* energy / stepHours;
		double upperP = (battery.getInitialStateOfChargePu() - battery.getMinStateOfChargePu())
				* energy / stepHours;
		addBounded("BatterySOC@" + battery.getId(), lowerP, upperP,
				toIntArray(columns), toDoubleArray(values));
	}

	private static int[] toIntArray(List<Integer> values) {
		int[] array = new int[values.size()];
		for (int i = 0; i < values.size(); i++) {
			array[i] = values.get(i).intValue();
		}
		return array;
	}

	private static double[] toDoubleArray(List<Double> values) {
		double[] array = new double[values.size()];
		for (int i = 0; i < values.size(); i++) {
			array[i] = values.get(i).doubleValue();
		}
		return array;
	}
}
