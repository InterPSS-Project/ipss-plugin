package org.interpss.sample.dclf_ca;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.algo.dclf.SenAnalysisType;
import com.interpss.simu.util.sample.SampleTestingCases;

public class SenSample {
	public static void main(String args[]) throws Exception {
		
		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleTestingCases.load_LF_5BusSystem(net);
		//System.out.println(net.net2String());

		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		
		/*
		 * GFS samples
		 */
		double x = algo.calGenShiftFactor("4", net.getBranch("1->3(1)"));    // w.r.p to the Ref Bus
		System.out.println("GSF Gen@Bus-4 on Branch 1->3(1): " + x);
		
		/*
		 * PTDF samples
		 */
		x = algo.pTransferDistFactor("4", net.getBranch("1->2(1)"));         // w.r.p to the Ref Bus
		System.out.println("PTDF Inject@Bus-4 on Branch 1->3(1): " + x);
		
		x = algo.pTransferDistFactor("1", "3", net.getBranch("2->3(1)"));
		System.out.println("PTDF Inject@Bus-1 Withdraw@Bus-3 on Branch 2->3(1): " + x);
		
		/*
		 * 	 calculate bus sensitivity dAngle(busId)/dP(injectBus).
		 */
		x = algo.calBusSensitivity(SenAnalysisType.PANGLE, "4"/* injectBusId */, "1"/* busId */);
		System.out.println("dAng('1')/dP('4'): " + x);
		
		/*
		 * 	 calculate bus sensitivity dV(busId)/dQ(injectBus).
		 */
		x = algo.calBusSensitivity(SenAnalysisType.QVOLTAGE, "3"/* injectBusId */, "1"/* busId */);
		System.out.println("dV('1')/dQ('3'): " + x);
	}	
}

