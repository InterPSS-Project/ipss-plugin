package org.ipss.multiNet.test;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.algo.SubNetworkProcessor;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.util.NumericUtil;
import org.ipss.multiNet.algo.MultiNetDStabSimuHelper;
import org.ipss.multiNet.equivalent.NetworkEquivUtil;
import org.ipss.multiNet.equivalent.NetworkEquivalent;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestNetworkEquiv {
	
	@Test
	public void test_IEEE9Bus_subNetEquiv() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
	    
	    /*
	     * Step-1  split the full network into two sub-network
	     */
	    
	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
	    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)");
	    proc.addSubNetInterfaceBranch("Bus7->Bus8(0)");
	    
	    proc.createSubNetworks();
	    
	    MultiNetDStabSimuHelper mNetDstabHelper = new  MultiNetDStabSimuHelper(dsNet,proc);
	    mNetDstabHelper.processInterfaceBranchEquiv();
	    
	    System.out.println(proc.getSubNet2BoundaryBusListTable());
	    
        NetworkEquivalent equiv=NetworkEquivUtil.calMultiNetworkEquiv(proc).get("SubNet-2");
        System.out.println(equiv.getMatrix()[0][0]);
        /*
         * ymatrix
         *  a(0,0): 0.0000 + j-15.7725
			a(0,1): -0.0000 + j16.0000
			
			a(1,1): 0.0000 + j-27.23596
			a(1,0): -0.0000 + j16.0000
			----------------------------------------------
			Matlab
			y=[-15.7725i,16.0000i;16.0000i,-27.23596i]

			y =
			
			   0.0000 -15.7725i   0.0000 +16.0000i
			   0.0000 +16.0000i   0.0000 -27.2360i
			
			>> inv(y)
			
			ans =
			
			   0.0000 + 0.1569i   0.0000 + 0.0922i
			   0.0000 + 0.0922i   0.0000 + 0.0909i
			
         */
        assertTrue(NumericUtil.equals(equiv.getMatrix()[0][0],new Complex(0.0, 0.15690803143610757),1.0E-6));
	}

}
