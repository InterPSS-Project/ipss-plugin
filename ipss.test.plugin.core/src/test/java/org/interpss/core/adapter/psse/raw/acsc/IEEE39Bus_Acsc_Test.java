package org.interpss.core.adapter.psse.raw.acsc;

import static org.junit.Assert.assertTrue;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.acsc.AcscModelParser;
import org.interpss.odm.mapper.ODMAcscParserMapper;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.acsc.AcscNetwork;

public class IEEE39Bus_Acsc_Test {
	
	@Test
	public void acsc_test() throws InterpssException{
		
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.AcscNet, new String[]{
				"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus_v30.raw",
				"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus_v30.seq"
		}));
		AcscModelParser acscParser =(AcscModelParser) adapter.getModel();
			
		
		AcscNetwork faultNet = new ODMAcscParserMapper().map2Model(acscParser).getAcscNet();
		//System.out.println(faultNet.net2String());
		
//	  	SimpleFaultAlgorithm algo = CoreObjectFactory.createSimpleFaultAlgorithm(faultNet);
//  		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus2", algo);
//		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
//		fault.setZLGFault(new Complex(0.0, 0.0));
//		fault.setZLLFault(new Complex(0.0, 0.0));
//		
//	  	algo.calculateBusFault(fault);
	}

}
