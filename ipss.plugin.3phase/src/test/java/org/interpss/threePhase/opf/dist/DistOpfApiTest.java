package org.interpss.threePhase.opf.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.opf.dist.constraint.DistOpfConstraintFactory;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;

import com.interpss.core.acsc.PhaseCode;
import com.interpss.opf.datatype.OpfConstraintType;

public class DistOpfApiTest {

	@Test
	public void factoryCreatesDistOpfAlgorithm() {
		DStabNetwork3Phase net = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
		DistOpfAlgorithm algorithm = ThreePhaseObjectFactory.createDistOpfAlgorithm(net);

		assertNotNull(algorithm);
		DistOpfResult result = algorithm.solve();
		assertEquals(DistOpfStatus.ERROR, result.getStatus());
		assertFalse(result.isSolved());
	}

	@Test
	public void variableIndexIsDeterministic() {
		DistOpfVariableIndex index = new DistOpfVariableIndex();

		assertEquals(0, index.branchP("l1", PhaseCode.A));
		assertEquals(1, index.branchQ("l1", PhaseCode.A));
		assertEquals(0, index.branchP("l1", PhaseCode.A));
		assertEquals(2, index.busV2("b2", PhaseCode.B));
		assertEquals(3, index.size());
	}

	@Test
	public void sparseConstraintFactoryBuildsEqualityRows() {
		assertEquals(OpfConstraintType.EQUALITY,
				DistOpfConstraintFactory.equality(0, "balance", 1.5,
						new int[] {0, 2}, new double[] {1.0, -1.0}).getCstType());
		assertEquals(2, DistOpfConstraintFactory.equality(0, "balance", 1.5,
				new int[] {0, 2}, new double[] {1.0, -1.0}).getColNo().size());
	}
}
