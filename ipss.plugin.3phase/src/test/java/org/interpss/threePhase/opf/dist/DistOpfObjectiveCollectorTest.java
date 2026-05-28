package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.EnumSet;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.opf.dist.model.DistOpfBusData;
import org.interpss.threePhase.opf.dist.model.DistOpfDerData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;
import org.interpss.threePhase.opf.dist.objective.CurtailmentMinObjectiveCollector;
import org.interpss.threePhase.opf.dist.objective.GenMaxObjectiveCollector;
import org.interpss.threePhase.opf.dist.objective.TargetSubstationPObjectiveCollector;
import org.interpss.threePhase.opf.dist.objective.TargetSubstationQObjectiveCollector;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfObjectiveCollectorTest {

	@Test
	public void curtailmentMinUsesCurtailmentVariables() {
		DistOpfModelData data = modelDataWithDer();
		DistOpfVariableIndex index = variableIndexForDer();
		double[] objective = new double[index.size()];

		new CurtailmentMinObjectiveCollector(data, index, objective).collectObjective();

		assertEquals(0.0, objective[index.derP("der-1", PhaseCode.A)], 1.0e-12);
		assertEquals(1.0, objective[index.curtailment("der-1", PhaseCode.A)], 1.0e-12);
		assertEquals(1.0, objective[index.curtailment("der-1", PhaseCode.B)], 1.0e-12);
		assertEquals(1.0, objective[index.curtailment("der-1", PhaseCode.C)], 1.0e-12);
	}

	@Test
	public void genMaxUsesNegativeActiveGenerationVariables() {
		DistOpfModelData data = modelDataWithDer();
		DistOpfVariableIndex index = variableIndexForDer();
		double[] objective = new double[index.size()];

		new GenMaxObjectiveCollector(data, index, objective).collectObjective();

		assertEquals(-1.0, objective[index.derP("der-1", PhaseCode.A)], 1.0e-12);
		assertEquals(-1.0, objective[index.derP("der-1", PhaseCode.B)], 1.0e-12);
		assertEquals(-1.0, objective[index.derP("der-1", PhaseCode.C)], 1.0e-12);
		assertEquals(0.0, objective[index.curtailment("der-1", PhaseCode.A)], 1.0e-12);
	}

	@Test
	public void targetObjectivesUsePositiveAndNegativeDeviationVariables() {
		DistOpfModelData data = modelDataWithDer();
		DistOpfVariableIndex index = variableIndexForDer();
		index.targetPPositive("source");
		index.targetPNegative("source");
		index.targetQPositive("source");
		index.targetQNegative("source");

		double[] pObjective = new double[index.size()];
		new TargetSubstationPObjectiveCollector(data, index, pObjective).collectObjective();
		assertEquals(1.0, pObjective[index.targetPPositive("source")], 1.0e-12);
		assertEquals(1.0, pObjective[index.targetPNegative("source")], 1.0e-12);
		assertEquals(0.0, pObjective[index.targetQPositive("source")], 1.0e-12);

		double[] qObjective = new double[index.size()];
		new TargetSubstationQObjectiveCollector(data, index, qObjective).collectObjective();
		assertEquals(1.0, qObjective[index.targetQPositive("source")], 1.0e-12);
		assertEquals(1.0, qObjective[index.targetQNegative("source")], 1.0e-12);
		assertEquals(0.0, qObjective[index.targetPPositive("source")], 1.0e-12);
	}

	private static DistOpfVariableIndex variableIndexForDer() {
		DistOpfVariableIndex index = new DistOpfVariableIndex();
		for (PhaseCode phase : EnumSet.of(PhaseCode.A, PhaseCode.B, PhaseCode.C)) {
			index.derP("der-1", phase);
			index.derQ("der-1", phase);
			index.curtailment("der-1", phase);
		}
		return index;
	}

	private static DistOpfModelData modelDataWithDer() {
		DistOpfBusData source = new DistOpfBusData("source", true, 12470.0,
				EnumSet.of(PhaseCode.A, PhaseCode.B, PhaseCode.C), new Complex3x1());
		DistOpfDerData der = new DistOpfDerData("der-1", "source",
				EnumSet.of(PhaseCode.A, PhaseCode.B, PhaseCode.C),
				new Complex3x1(new Complex(0.04, 0.0), new Complex(0.04, 0.0), new Complex(0.04, 0.0)));
		return new DistOpfModelData(1.0, "source", Collections.singletonList(source),
				Collections.emptyList(), Collections.singletonList(der),
				Collections.emptyMap(), Collections.emptyMap());
	}
}
