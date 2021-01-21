package org.interpss.multiNet.test.unit;

import static org.junit.Assert.assertTrue;

import java.util.Hashtable;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.multiNet.algo.MultiNetDStabSimuHelper;
import org.interpss.multiNet.algo.SubNetworkProcessor;
import org.interpss.multiNet.equivalent.NetworkEquivalent;
import org.interpss.numeric.matrix.MatrixUtil;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.NetCoordinate;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestMultiNetDStabSimuHelper {
	
	
	/**
	 * Test the subnetwork equivalent
	 * @throws InterpssException
	 */
	@Test
	public void test_posSeqSubNetEquiv_IEEE9Bus() throws InterpssException{
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
		
		
	    DStabilityNetwork dsNet = (DStabilityNetwork) simuCtx.getDStabilityNet();
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		 proc.addSubNetInterfaceBranch("Bus4->Bus5(0)");
		 proc.addSubNetInterfaceBranch("Bus7->Bus8(0)");
		    
		 proc.splitFullSystemIntoSubsystems(false);
		    
		    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
		    // this must be set before initializing MultiNet3Ph3SeqDStabSimuHelper
		// proc.set3PhaseSubNetByBusId("Bus5");
		    
		 // "SubNet-1": buses 1,3,4,6,8,9
		 assertTrue(proc.getSubNetwork("SubNet-1").getNoActiveBus() ==6);
		//  System.out.println(proc.getSubNetwork("SubNet-1").net2String());
		    
		  MultiNetDStabSimuHelper  mNetHelper = new MultiNetDStabSimuHelper(dsNet,proc);
		  
		  mNetHelper.calculateSubNetTheveninEquiv();
		  Hashtable<String, NetworkEquivalent> equivTable = mNetHelper.getSubNetEquivTable();
		  
		  
		  NetworkEquivalent equiv_subNet1=equivTable.get("SubNet-1") ;
		  assertTrue(equiv_subNet1.getEquivCoordinate()==NetCoordinate.POSITIVE_SEQUENCE);
		  /*
		   *  SubNet-1 eqv: 
			  (0.006112387800872096, 0.07952815643935975), (0.0056247409015727804, 0.02842605562067255), 
			  (0.005624740901572781, 0.02842605562067255), (0.058441502171763686, 0.19529838105182823), 
		   */
		  
		  System.out.println("SubNet-1 eqv: \n"+MatrixUtil.complex2DAry2String(equiv_subNet1.getComplexEqn().getA()));
		  assertTrue(NumericUtil.equals(equiv_subNet1.getComplexEqn().getAij(0,0), new Complex(0.006112387800872096, 0.07952815643935975),1.0E-6));
		  assertTrue(NumericUtil.equals(equiv_subNet1.getComplexEqn().getAij(0,1), new Complex(0.0056247409015727804, 0.02842605562067255),1.0E-6));
		  assertTrue(NumericUtil.equals(equiv_subNet1.getComplexEqn().getAij(1,0), new Complex(0.005624740901572781, 0.02842605562067255),1.0E-6));
		  assertTrue(NumericUtil.equals(equiv_subNet1.getComplexEqn().getAij(1,1), new Complex(0.058441502171763686, 0.19529838105182823),1.0E-6));
		   // "SubNet-2": buses 2,5,7
		  NetworkEquivalent equiv_subNet2=equivTable.get("SubNet-2") ;
		  assertTrue(equiv_subNet2.getEquivCoordinate()==NetCoordinate.POSITIVE_SEQUENCE);
		  

          /*
			  SubNet-2 eqv: 
			  (0.11337683562544085, 0.24325417157416276), (0.04343351865455726, 0.1244334898321662), 
			  (0.04343351865455726, 0.12443348983216619), (0.022819398397765443, 0.14317671541236235), 
		   */
		  System.out.println("SubNet-2 eqv: \n"+MatrixUtil.complex2DAry2String(equiv_subNet2.getComplexEqn().getA()));
		  
		  assertTrue(NumericUtil.equals(equiv_subNet2.getComplexEqn().getAij(0,0), new Complex(0.11337683562544085, 0.24325417157416276),1.0E-6));
		  assertTrue(NumericUtil.equals(equiv_subNet2.getComplexEqn().getAij(0,1), new Complex(0.04343351865455726, 0.1244334898321662),1.0E-6));
		  assertTrue(NumericUtil.equals(equiv_subNet2.getComplexEqn().getAij(1,0), new Complex(0.04343351865455726, 0.1244334898321662),1.0E-6));
		  assertTrue(NumericUtil.equals(equiv_subNet2.getComplexEqn().getAij(1,1), new Complex(0.022819398397765443, 0.14317671541236235),1.0E-6));
		  
		  
		  
		  

		  
		  /*
		   * I_5_4 = (-0.3867573986015036, 0.32889899992105764)
			I_7_8 = (0.7474676563184014, -0.019730840454291472)
			
			id: SubNet-2
			current injection
			{Bus7=(-0.7474658383252337, 0.019732069636157826), Bus5=(0.3867535650163796, -0.3288965566018909)}
			
			Vth@Bus5=(0.9044667025525787, -0.033280313137949366)
			Vth@Bus7= (0.9857627307450353, 0.13992046700786243)
			
			
			id: SubNet-1
			current injection
			{Bus8=(0.7474658383252337, -0.019732069636157826), Bus4=(-0.3867535650163796, 0.3288965566018909)}
			
			Vth@Bus4(1.0489797165693315, -0.031446871024138096),
			Vth@Bus8 (0.9798184356846653, -0.12214059523195701)
		   */
		  
		  
	}

}
