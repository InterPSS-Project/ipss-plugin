package org.interpss.core.dstab.dynLoad;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.spring.CoreCommonSpringFactory;

public class TestCMPLDWModel {
	
	@Test
	public void testPTIExample() throws InterpssException{
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonSpringFactory.getIpssMsgHub();
		IpssLogger.getLogger().setLevel(Level.WARNING);
		
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/threeBus_cmpldw.raw",
				"testData/adpter/psse/v30/threeBus_cmpldw.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
	    
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
	  	algo.setLfMethod(AclfMethod.PQ);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  	
  		assertTrue( dsNet.isLfConverged());
  		
  		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
	    
	}

}
