package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.odm.mapper.ODMAclfParserMapper;
import org.junit.Test;

import com.interpss.simu.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class PSSE_ACTIVSg2000Bus_Test  extends CorePluginTestSetup {
		
		@Test
		public void test_ACTIVSg2000_Dstab() throws InterpssException{
			IpssCorePlugin.init();
			IpssLogger.getLogger().setLevel(Level.WARNING);
			PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_33);
			assertTrue(adapter.parseInputFile(
					"testData/adpter/psse/v33/ACTIVSg2000/ACTIVSg2000.raw"));
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
			System.out.println(AclfOutFunc.loadFlowSummary(net));
	
			
		}
		
		
		

}
