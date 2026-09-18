package org.interpss.cim;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.fadapter.cim.CGMESDirectParser;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.PVBusLimit;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * IEEE118 CIM Hub vs MatPower sample.
 *
 * <p>The CIM Hub IEEE118 fixture (and companion {@code IEEE118.m}) schedules
 * ~2862 MW gen against ~4242 MW load with machines on LV GSU stubs. Flat-start NR
 * cannot deliver the slack; both imports apply the same cold-start prep as
 * OpenCIM {@code CIM17_Ieee118Sample}.
 */
public class IEEE118CimSample {

	private static final String TD = "testData/adpter/cim/cgmes3.0/";
	private static final String CIM_FILE = TD + "IEEE118_CIM.xml";
	private static final String MATPOWER_FILE = TD + "IEEE118.m";

	public static void main(String args[]) throws Exception {
		AclfNetwork cimNet = new CGMESDirectParser().parse(CIM_FILE);

		AclfNetwork matNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.MATPOWER)
				.load(MATPOWER_FILE)
				.getAclfNet();

		System.out.println("CIM Network: " + cimNet.getNoBus() + " buses, " + cimNet.getNoBranch() + " branches");
		prepareCimHubColdStart(cimNet);
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(cimNet);
		algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		algo.loadflow();
		System.out.println("CIM Loadflow converged: " + cimNet.isLfConverged());

		System.out.println("MatPower Network: " + matNet.getNoBus() + " buses, " + matNet.getNoBranch() + " branches");
		prepareCimHubColdStart(matNet);
		algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(matNet);
		algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		algo.loadflow();
		System.out.println("MatPower Loadflow converged: " + matNet.isLfConverged());
	}

	/**
	 * Scale loads to scheduled gen when the CIM Hub export is imbalanced, prefer
	 * the strongest machine as swing, and disable PV Q-limit adjustment.
	 */
	static void prepareCimHubColdStart(AclfNetwork net) throws Exception {
		double sumGenP = 0.0;
		double sumLoadP = 0.0;
		for (AclfBus bus : net.getBusList()) {
			if (bus.isGen())
				sumGenP += bus.getGenP();
			if (bus.isLoad())
				sumLoadP += bus.getLoadP();
		}
		if (sumLoadP > 1.0e-6 && sumGenP > 1.0e-6 && sumLoadP > sumGenP * 1.05) {
			double scale = sumGenP / sumLoadP;
			System.out.printf(
					"CIM Hub schedule imbalance: sumGenP=%.4f pu, sumLoadP=%.4f pu — scaling loads by %.4f%n",
					sumGenP, sumLoadP, scale);
			for (AclfBus bus : net.getBusList()) {
				if (bus.getContributeLoadList() == null)
					continue;
				for (AclfLoad load : bus.getContributeLoadList()) {
					Complex s = load.getLoadCP();
					if (s != null)
						load.setLoadCP(new Complex(s.getReal() * scale, s.getImaginary() * scale));
				}
			}
			net.initContributeGenLoad(false);
		}

		AclfBus swing = null;
		AclfBus bestPv = null;
		double bestAbsP = -1.0;
		for (AclfBus bus : net.getBusList()) {
			if (!bus.isActive())
				continue;
			if (bus.getGenCode() == AclfGenCode.SWING)
				swing = bus;
			if (bus.isGen()) {
				double absP = Math.abs(bus.getGenP());
				if (absP > bestAbsP) {
					bestAbsP = absP;
					bestPv = bus;
				}
			}
			if (bus.isPVBusLimit()) {
				PVBusLimit lim = bus.getPVBusLimit();
				lim.setControlStatus(false);
				lim.setAdjustStatus(false);
			}
		}
		if (bestPv != null && (swing == null || Math.abs(swing.getGenP()) + 1.0e-6 < bestAbsP)) {
			if (swing != null && swing != bestPv) {
				swing.setGenCode(AclfGenCode.GEN_PV);
				swing.toPVBus().setDesiredVoltMag(1.0, UnitType.PU);
			}
			bestPv.setGenCode(AclfGenCode.SWING);
			bestPv.toSwingBus().setDesiredVoltMag(1.0, UnitType.PU);
			bestPv.toSwingBus().setDesiredVoltAng(0.0, UnitType.Rad);
			System.out.println("Swing bus set to strongest gen: " + bestPv.getName()
					+ " (P=" + bestPv.getGenP() + " pu)");
		}
	}
}
