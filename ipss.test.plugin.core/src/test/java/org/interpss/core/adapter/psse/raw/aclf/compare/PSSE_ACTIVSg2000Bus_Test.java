package org.interpss.core.adapter.psse.raw.aclf.compare;

import java.util.logging.Level;

import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.odm.mapper.ODMAclfParserMapper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfNetObjectComparator;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class PSSE_ACTIVSg2000Bus_Test  extends CorePluginTestSetup {
	
	@Test
	public void test_ACTIVSg2000_Compare() throws InterpssException{
		IpssCorePlugin.init();
		IpssLogger.getLogger().setLevel(Level.WARNING);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_36);
		assertTrue(adapter.parseInputFile(
				"testData/psse/v36/Texas2k/Texas2k_series24_case1_2016summerPeak_v36.RAW"));
		AclfModelParser parser =(AclfModelParser) adapter.getModel();
		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
		AclfNetwork net =simuCtx.getAclfNet();
		
	  
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(net);
		
		//aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

		//aclfAlgo.getLfAdjAlgo().setPowerAdjAppType(AdjustApplyType.POST_ITERATION);
		aclfAlgo.getLfAdjAlgo().setPowerAdjust(false);
		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		aclfAlgo.setTolerance(1.0E-6);
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(net));

		
		// Verify bus voltage magnitudes and angles for key buses
		//bus 1001 voltage mag = 0.9781, angle = -26.51
		assertEquals(net.getBus("Bus1001").getVoltageMag(), 0.9781, 1.0E-4);
		assertEquals(net.getBus("Bus1001").getVoltageAng(), -26.51*Math.PI/180, 1.0E-2);

		//bus 7280 voltage mag = 1.0058, angle = -34.21
		assertEquals(net.getBus("Bus7280").getVoltageMag(), 1.0058, 1.0E-4);
		assertEquals(net.getBus("Bus7280").getVoltageAng(), -34.21*Math.PI/180, 1.0E-2);

		//bus 8160 voltage mag = 1.0059, angle = -51.73
		assertEquals(net.getBus("Bus8160").getVoltageMag(), 1.0059, 1.0E-4);
		assertEquals(net.getBus("Bus8160").getVoltageAng(), -51.73*Math.PI/180, 1.0E-2);
		
  		AclfNetwork aclfNetCopy = net.jsonCopy();
		
  		AclfNetObjectComparator comp = new AclfNetObjectComparator(net, aclfNetCopy);
  		comp.compareNetwork();
  		
  		System.out.println("Differences found: " + comp.getDiffMsgList());
  		//assertTrue("" + comp.getDiffMsgList(), comp.getDiffMsgList().size() == 0);
	}		
	
}
