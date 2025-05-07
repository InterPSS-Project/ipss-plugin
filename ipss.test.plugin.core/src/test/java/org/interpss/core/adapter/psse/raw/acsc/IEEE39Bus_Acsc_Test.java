package org.interpss.core.adapter.psse.raw.acsc;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.acsc.AcscModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.util.TestUtilFunc;
import org.interpss.odm.mapper.ODMAcscParserMapper;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.sc.SimpleFaultAlgorithm;

public class IEEE39Bus_Acsc_Test {
	
	@Test
	public void acsc_test() throws InterpssException{
		IpssCorePlugin.init();
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.AcscNet, new String[]{
				"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus_v30.raw",
				"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus_v30.seq"
		}));
		AcscModelParser acscParser =(AcscModelParser) adapter.getModel();
			
		
		AcscNetwork net = new ODMAcscParserMapper().map2Model(acscParser).getAcscNet();
		//System.out.println(faultNet.net2String());
		
	  	SimpleFaultAlgorithm algo = CoreObjectFactory.createSimpleFaultAlgorithm(net);
 		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus2", algo, true);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
	  	algo.calBusFault(fault);

		System.out.println(fault.getFaultResult().getSCCurrent_012());
	  	System.out.println(fault.getFaultResult().getBusVoltage_012(net.getBus("Bus1")));
	  	
	  	//3p fault @Bus2
	  	//fault current
	  	//0.0000 + j0.0000  -1.92844 + j36.48182  0.0000 + j0.0000
	  	assertTrue(TestUtilFunc.compare(fault.getFaultResult().getSCCurrent_012(), 
	  			0.0, 0.0, -1.92844, 36.48182, 0.0, 0.0) );
	  	//voltage @Bus1
	  	//0.0000 + j0.0000  0.55351 + j-0.01353  0.0000 + j0.0000
	  	//IBusScVoltage busResult = (IBusScVoltage)fault.getFaultResult();
	  	assertTrue(TestUtilFunc.compare(fault.getFaultResult().getBusVoltage_012(net.getBus("Bus1")), 
	  			0.0, 0.0, 0.55351, -0.01353, 0.0, 0.0) );
	}

}
