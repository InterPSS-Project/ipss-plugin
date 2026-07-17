package org.interpss.sample.dclf_ca;

import org.interpss.display.DclfOutFunc;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.DclfAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBus;
import com.interpss.simu.util.sample.SampleTestingCases;

public class DclfSample {
	public static void main(String args[]) throws Exception {
		
		AclfNetwork aclfNet = CoreObjectFactory.createAclfNetwork();
		SampleTestingCases.load_LF_5BusSystem(aclfNet);
		//System.out.println(net.net2String());

		DclfAlgorithm dclfAlgo = DclfAlgoObjectFactory.createDclfAlgorithm(aclfNet);
		
		// perform DCLF calculation
		dclfAlgo.calculateDclf();
		
		DclfOutFunc.commaDelimited = true;
		System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false /*branchViolation*/));
		
		System.out.println("\nBase Case\n==========");
		
		System.out.println("Bus-1 P: " + dclfAlgo.getBusPower("1"));
		System.out.println("Bus-1 Ang: " + dclfAlgo.getBusAngle("1"));
		System.out.println("Bus-1 P in to the network: " + dclfAlgo.getBusPowerInfoNet("1"));

		System.out.println("Bus-4 P: " + dclfAlgo.getBusPower("4"));
		System.out.println("Bus-4 Ang: " + dclfAlgo.getBusAngle("4"));
		System.out.println("Bus-4 P in to the network: " + dclfAlgo.getBusPowerInfoNet("4"));
		
		System.out.println("Bus-5 P: " + dclfAlgo.getBusPower("5"));
		System.out.println("Bus-5 Ang: " + dclfAlgo.getBusAngle("5"));
		System.out.println("Bus-5 P in to the network: " + dclfAlgo.getBusPowerInfoNet("5"));
		
		System.out.println("\nGen/Load Change Case\n======================");
		
		DclfAlgoBus dclfBus1 = dclfAlgo.getDclfAlgoBus("1");
		dclfBus1.setLoadAdjust(0.2);
		
		DclfAlgoBus dclfBus4 = dclfAlgo.getDclfAlgoBus("4");
		dclfBus4.setGenAdjust(0.1);
		
		// perform DCLF calculation
		dclfAlgo.calculateDclf();
		
		System.out.println("Bus-1 P: " + dclfAlgo.getBusPower("1"));
		System.out.println("Bus-1 Ang: " + dclfAlgo.getBusAngle("1"));
		System.out.println("Bus-1 P in to the network: " + dclfAlgo.getBusPowerInfoNet("1"));

		System.out.println("Bus-4 P: " + dclfAlgo.getBusPower("4"));
		System.out.println("Bus-4 Ang: " + dclfAlgo.getBusAngle("4"));
		System.out.println("Bus-4 P in to the network: " + dclfAlgo.getBusPowerInfoNet("4"));
		
		System.out.println("Bus-5 P: " + dclfAlgo.getBusPower("5"));
		System.out.println("Bus-5 Ang: " + dclfAlgo.getBusAngle("5"));
		System.out.println("Bus-5 P in to the network: " + dclfAlgo.getBusPowerInfoNet("5"));
		
		/*
		 * At this point, the aclfNet object is untouched 
		 */
		
		// transfer Dclf results to the AclfNetwork object
		dclfAlgo.transfer2AclfNet(true /* clearGenLoadAdjInfo */); 
	}	
}

