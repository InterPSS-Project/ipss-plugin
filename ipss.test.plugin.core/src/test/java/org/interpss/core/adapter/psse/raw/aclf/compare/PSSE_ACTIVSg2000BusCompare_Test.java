package org.interpss.core.adapter.psse.raw.aclf.compare;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.odm.mapper.ODMAclfParserMapper;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfNetObjectComparator;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class PSSE_ACTIVSg2000BusCompare_Test  extends CorePluginTestSetup {
	
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
		
		/*
		 * All SwitchedShuntDevice remote bus branch id are 20, which is wrong.
		 */
		//AclfBus bus1 = net.getBus("Bus1010");
		//assertTrue("", bus1.getSwitchedShunt().getRemoteBusBranchId().equals("20"));
		
		/*
		 * The bus control/PV bus limit and switched shunt device can not co-exit at a bus.
		 */
//		AclfBus bus2 = net.getBus("Bus1033");
//		assertTrue("", bus2.getBusControl() != null && bus2.getPVBusLimit() != null &&
//					bus2.getSwitchedShunt() != null);
	
		/*
		AclfBusState busState = new AclfBusState(bus);
		AclfBus bus2 = AclfBusState.create(busState);
		*/
		
  		AclfNetwork aclfNetCopy = net.jsonCopy();
  		
		//AclfBus busCopy = aclfNetCopy.getBus("Bus1010");
		
  		AclfNetObjectComparator comp = new AclfNetObjectComparator(net, aclfNetCopy);
  		comp.compareNetwork();
  		
  		//System.out.println("Differences found: " + comp.getDiffMsgList());
  		assertTrue("" + comp.getDiffMsgList(), comp.getDiffMsgList().size() == 0);
	}		
	
}
