package sample.adj;

import org.interpss.CorePluginFactory;
import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.AclfAdjustObjectFactory;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.ShuntCompensator;
import com.interpss.core.aclf.ShuntCompensatorType;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.AclfAdjustControlType;
import com.interpss.core.aclf.facts.StaticVarCompensator;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class Ieee14PVLimit_SVCSample {
	public static void main(String[] args) throws InterpssException {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		AclfBus bus8 = aclfNet.getBus("Bus8");
		bus8.getContributeGenList().get(0).setQGenLimit(new LimitType(0.1, -0.06));
		
		StaticVarCompensator svc = AclfAdjustObjectFactory
				.createStaticVarCompensator(bus8, AclfAdjustControlMode.CONTINUOUS, AclfAdjustControlType.POINT_CONTROL).get();

		svc.setBInit(0.0);
		svc.setBLimit(new LimitType(0.1, 0.0));
		svc.setDesiredControlRange(new LimitType(1.1, 0.9));
		
		ShuntCompensator bank1 = CoreObjectFactory.createShuntCompensator(svc, ShuntCompensatorType.CAPACITOR);
		bank1.setId("Bank1");
		bank1.setSteps(1);
		bank1.setUnitQMvar(0.5);
		bank1.setB(0.05);
		
		ShuntCompensator bank2 = CoreObjectFactory.createShuntCompensator(svc, ShuntCompensatorType.CAPACITOR);
		bank2.setId("Bank2");
		bank2.setSteps(1);
		bank2.setUnitQMvar(0.5);
		bank2.setB(0.1);

		// we set the svc to be off control status, it will be turned on after the PVBusLimit
		// hits the limit.
		svc.setStatus(false);
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.setMaxIterations(20);
	  	algo.setTolerance(0.0001, UnitType.PU, aclfNet.getBaseKva());
	  	algo.loadflow();
	  	
  		//System.out.println(aclfNet.net2String());
	  	
		System.out.println("MaxMismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
        //printout the power flow results
 		System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));
	}
}
