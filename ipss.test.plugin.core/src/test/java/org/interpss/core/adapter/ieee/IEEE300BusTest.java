package org.interpss.core.adapter.ieee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.PSXfrPControl;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;

public class IEEE300BusTest extends CorePluginTestSetup {
	@Test
	public void xtestCase5() throws Exception{
		AclfNetwork net = loadIeee300();
		
  		assertTrue((net.getBusList().size() == 300 && net.getBranchList().size() == 411));

	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
	  	assertTrue(net.isLfConverged());		
 		AclfBus swingBus = (AclfBus)net.getBus("Bus7049");
 		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()).re);
		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()).im);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-4.57025)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-0.39049)<0.0001);
	}

	@Test
	public void testIEEE300ControlIsolation() throws Exception {
		PSXfrPControl psXfrControl = loadIeee300().getBranch("Bus196->Bus2040(1)").getPSXfrPControl();
		assertEquals(-15.0, psXfrControl.getAngLimit(UnitType.Deg).getMin(), 1.0E-6);
		assertEquals(0.0, psXfrControl.getAngLimit(UnitType.Deg).getMax(), 1.0E-6);

		AclfNetwork allControlsNet = runIeee300Loadflow(algo -> {
		});
		assertTrue(allControlsNet.isLfConverged());
		assertIEEE300PhaseShiftControlObjective(allControlsNet);

		assertTrue(runIeee300Loadflow(algo -> {
			algo.getLfAdjAlgo().getVoltAdjConfig().setAdjust(false);
		}).isLfConverged());

		assertTrue(runIeee300Loadflow(algo -> {
			algo.getLfAdjAlgo().getLimitCtrlConfig().setAdjust(false);
		}).isLfConverged());

		assertTrue(runIeee300Loadflow(algo -> {
			algo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
		}).isLfConverged());

		assertTrue(runIeee300Loadflow(algo -> {
			algo.getLfAdjAlgo().getPowerAdjConfig().setPsXfrPControl(false);
		}).isLfConverged());
	}

	private AclfNetwork runIeee300Loadflow(Consumer<LoadflowAlgorithm> configureAlgo) throws Exception {
		AclfNetwork net = loadIeee300();
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		configureAlgo.accept(algo);
		algo.loadflow();
		return net;
	}

	private AclfNetwork loadIeee300() throws Exception {
		return CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee300.ieee")
				.getAclfNet();
	}

	private void assertIEEE300PhaseShiftControlObjective(AclfNetwork net) {
		PSXfrPControl psXfrControl = net.getBranch("Bus196->Bus2040(1)").getPSXfrPControl();
		LimitType angleLimit = psXfrControl.getAngLimit(UnitType.Deg);
		double angle = psXfrControl.getParentBranch().toPSXfr().getFromAngle(UnitType.Deg);
		assertTrue(angle >= angleLimit.getMin() - 1.0E-6,
				"angle=" + angle + ", angleLimit=" + angleLimit);
		assertTrue(angle <= angleLimit.getMax() + 1.0E-6,
				"angle=" + angle + ", angleLimit=" + angleLimit);

		LimitType pRange = psXfrControl.getDesiredControlRange();
		double p = getMeasuredActivePower(psXfrControl);
		System.out.printf("IEEE300 PSXfr control %s: P=%.6f pu, PRange=%s, angle=%.6f deg, angleLimit=%s%n",
				psXfrControl.getParentBranch().getId(), p, pRange, angle, angleLimit);
		assertTrue(p >= pRange.getMin() - 1.0E-4,
				"p=" + p + ", pRange=" + pRange + ", angle=" + angle + ", angleLimit=" + angleLimit);
		assertTrue(p <= pRange.getMax() + 1.0E-4,
				"p=" + p + ", pRange=" + pRange + ", angle=" + angle + ", angleLimit=" + angleLimit);
	}

	private double getMeasuredActivePower(PSXfrPControl psXfrControl) {
		AclfBranch branch = psXfrControl.getParentBranch();
		double p;
		if (psXfrControl.isMeteredOnFromSide()) {
			p = branch.powerFrom2To(UnitType.PU).getReal();
			if (!psXfrControl.isFlowFrom2To()) {
				p = -p;
			}
		} else {
			p = branch.powerTo2From(UnitType.PU).getReal();
			if (psXfrControl.isFlowFrom2To()) {
				p = -p;
			}
		}
		return p;
	}
}
