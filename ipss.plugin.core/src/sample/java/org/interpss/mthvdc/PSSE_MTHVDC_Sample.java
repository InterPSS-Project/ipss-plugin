package org.interpss.mthvdc;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetModelType;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLineMT;
import com.interpss.core.aclf.hvdc.HvdcMTConverter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.LoadflowAlgoObjectFactory;

import org.interpss.fadapter.psse.PSSEDirectParser;
/**
 * Load {@code psse_mthvdc.raw} and print the multi-terminal HVDC model.
 * Automated coverage: {@code org.interpss.core.adapter.psse.raw.aclf.PSSE_MTHVDC_Test}.
 */
public class PSSE_MTHVDC_Sample {
	public static void main(String args[]) throws Exception {
		AclfNetwork aclfNet = new PSSEDirectParser().parse("ipss.plugin.core/testData/psse/v30/psse_mthvdc.raw");

/*
 * InterPSS only treats a branch as ZBR when |Z| ≤ zeroZBranchThreshold. The default threshold is 1e-5, 
   so this branch is not classified as ZBR. With equal voltages on both sides, Ybus flow is ~0 and you get 
   large opposite NR mismatches on 153/3006.

	Those two lines fix that for the sample:

	aclfNet.setZeroZBranchThreshold(1e-3) — makes X=1e-4 qualify as a zero-Z branch.
	aclfNet.setAclfNetModelType(ZBR_MODEL) — identifies the freshly parsed network as an explicit
	zero-Z model so the augmented NR equations account for those buses correctly.
 */		
		aclfNet.setZeroZBranchThreshold(1.0e-3);
		aclfNet.setAclfNetModelType(AclfNetModelType.ZBR_MODEL);

		//initCondition(aclfNet);		

		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);

		aclfAlgo.getNrMethodConfig().setNonDivergent(false);
		//aclfAlgo.getNrMethodConfig().setOptAlgo(NrOptimizeAlgoType.CUBIC_EQN);
		
		aclfAlgo.setTolerance(1.0E-6);
		aclfAlgo.setMaxIterations(100);
				
		aclfAlgo.loadflow();
	}

	private static void initCondition(AclfNetwork aclfNet) {
		System.out.println("MTDC lines: " + aclfNet.getHvdcLineMTList().size());
		for (HvdcLineMT mt : aclfNet.getHvdcLineMTList()) {
			mt.initLoadflow();
			System.out.println("  " + mt.getId()
					+ " mode=" + mt.getControlMode()
					+ " VCONV=" + mt.getVConvBusId()
					+ " VCMOD=" + mt.getVcMod()
					+ " nConv=" + mt.getConverterList().size()
					+ " nDcBus=" + mt.getDcBusList().size()
					+ " nLink=" + mt.getDcLinkList().size()
					+ " topo=" + (mt.validateTopology() == null ? "ok" : mt.validateTopology()));
			for (HvdcMTConverter c : mt.getConverterList()) {
				System.out.println("    conv " + c.getRefBusId()
						+ " SETVL=" + c.getSetValue()
						+ " CNVCOD=" + c.getCnvCod()
						+ " dcBus=" + c.getDcBusNumber()
						+ " PacMW=" + c.getPac()
						+ " QacMvar=" + c.getQac()
						+ " PQpu=" + mt.powerIntoConverter(c.getRefBusId()));
			}
		}

		aclfNet.getSpecialBranchList().forEach(branch -> {
			if (branch instanceof HvdcLine2TLCC) {
				((HvdcLine2TLCC<?>) branch).initLoadflow();
			}
		});

		aclfNet.calExternalPowerIntoNet();

		System.out.println("Buses with |mismatch| > 0.1:");
		for (AclfBus bus : aclfNet.getBusList()) {
			if (bus.mismatch(AclfMethodType.NR).abs() > 1e-1)
				System.out.println(bus.getId() + ", " + bus.mismatch(AclfMethodType.NR));
		}
	}
}
