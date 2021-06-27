package org.interpss.sample.acsc;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AcscOutFunc;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.sc.SimpleFaultAlgorithm;
import com.interpss.simu.util.sample.SampleTestingCases;

public class AcscSample {

	public static void main(String[] args)  throws InterpssException {
		IpssCorePlugin.init();
		
		unitVoltTest();
	}
	
	public static void unitVoltTest() throws InterpssException {
  		AcscNetwork faultNet = CoreObjectFactory.createAcscNetwork();
		SampleTestingCases.load_SC_5BusSystem(faultNet);
		
		//System.out.println(faultNet.net2String());
		
	  	SimpleFaultAlgorithm algo = CoreObjectFactory.createSimpleFaultAlgorithm(faultNet);
  		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("2", algo);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
	  	algo.calBusFault(fault, true /* cacheBusScVolt */ );
		System.out.println("//////////////////////////////////////////////////////");		
		System.out.println("----------- Fault using UnitVolt ---------------------");		
		System.out.println(AcscOutFunc.faultResult2String(faultNet, algo));		
	}
}
