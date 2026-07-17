package org.interpss.sample.aclf;

import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.exp.IpssNumericException;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.AclfAdjustObjectFactory;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.PVBusLimit;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.util.sample.SampleTestingCases;


public class PVGenQLimitControlSample {
	public static void main(String args[]) throws IpssNumericException, InterpssException {
		IpssCorePlugin.init();
		
  		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
  		SampleTestingCases.load_LF_5BusSystem(net);
		//System.out.println(net.net2String());

		net.getBus("1").setLoadCode(AclfLoadCode.CONST_Z);
		
		AclfBus bus = net.getBus("4");
		PVBusLimit pvLimit = AclfAdjustObjectFactory.createPVBusLimit(bus);
		pvLimit.setQLimit(new LimitType(1.4, 0.0));
		
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.setMaxIterations(20);
	  	algo.setTolerance(0.0001, UnitType.PU, net.getBaseKva());
	  	algo.loadflow();
//  		System.out.println(net.net2String());
	  	
  		assert net.isLfConverged();
  		
  		AclfBus swingBus = net.getBus("5");
		AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		System.out.println("Swing bus P, Q: " + swing.getGenResults(UnitType.PU));
	}
}
