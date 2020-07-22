package test.piecewise;

import static org.junit.Assert.assertTrue;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.piecewise.SubAreaNetProcessor;
import org.interpss.piecewise.seq012.CuttingBranch012;
import org.interpss.piecewise.seq012.SubArea012;
import org.interpss.piecewise.seq012.SubDStabNetwork;
import org.interpss.piecewise.seq012.impl.SubAreaDStabProcessorImpl;
import org.interpss.piecewise.seq012.impl.SubNetworkDStabProcessorImpl;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabLoad;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class IEEE9BusTestDStabSubAreaNet {
	
	@Test
	public void testSubArea() throws InterpssException{
		IpssCorePlugin.init();
	
		BaseDStabNetwork dsNet = getTestNet();
	    
	    /*
	    	Cutting branches "Bus5->Bus7(0)", "Bus7->Bus8(0)"
	    
	    	It breaks the network into two sub-areas
	    
	       		[ Bus1, Bus3, Bus4, Bus5, Bus6, Bus8, Bus9 ]
           		[ Bus2, Bus7 ]
	     */
	    
		SubAreaNetProcessor<BaseDStabBus<DStabGen,DStabLoad>, DStabBranch, SubArea012, Complex3x1> proc = 
				new SubAreaDStabProcessorImpl<SubArea012>(dsNet, new CuttingBranch012[] { 
						new CuttingBranch012("Bus5->Bus7(0)"),
						new CuttingBranch012("Bus7->Bus8(0)")});	
  		
  		proc.processSubAreaNet();
  		
  		proc.getSubAreaNetList().forEach(subarea -> {
  			//System.out.println(subarea);
  		});		
  		// System.out.println(dsNet.net2String());

  		assertTrue("We should have total two SubAreas", proc.getSubAreaNetList().size() == 2);

  		assertTrue("Bus1 should be in the SubArea (1)", dsNet.getBus("Bus1").getSubAreaFlag() == 1);
  		assertTrue("Bus9 should be in the SubArea (1)", dsNet.getBus("Bus9").getSubAreaFlag() == 1);
  		
  		assertTrue("Bus2 should be in the SubArea (2)", dsNet.getBus("Bus2").getSubAreaFlag() == 2);
  		assertTrue("Bus7 should be in the SubArea (2)", dsNet.getBus("Bus7").getSubAreaFlag() == 2);
	}

	@Test
	public void testSubNetwork() throws InterpssException{
		IpssCorePlugin.init();
	
		BaseDStabNetwork dsNet = getTestNet();
	    
	    SubAreaNetProcessor<BaseDStabBus<DStabGen,DStabLoad>, DStabBranch, SubDStabNetwork, Complex3x1> proc = 
				new SubNetworkDStabProcessorImpl<SubDStabNetwork>(dsNet, new CuttingBranch012[] { 
						new CuttingBranch012("Bus5->Bus7(0)"),
						new CuttingBranch012("Bus7->Bus8(0)")});	
  		
  		proc.processSubAreaNet();
  		
  		//proc.getSubAreaNetList().forEach(subarea -> {
  		//	System.out.println(subarea);
  		//});		
  		// System.out.println(dsNet.net2String());

  		assertTrue("We should have total two SubAreas", proc.getSubAreaNetList().size() == 2);

  		assertTrue("SubArea (1) should have 2 interface buses", proc.getSubAreaNet(1).getInterfaceBusIdList().size() == 2);
  		assertTrue("SubArea (1) should have 7 buses", proc.getSubAreaNet(1).getSubNet().getBusList().size() == 7);

  		assertTrue("Bus1 should be in the SubArea (1)", dsNet.getBus("Bus1").getSubAreaFlag() == 1);
  		assertTrue("Bus9 should be in the SubArea (1)", dsNet.getBus("Bus9").getSubAreaFlag() == 1);
  		
  		assertTrue("SubArea (1) should have 1 interface bus", proc.getSubAreaNet(2).getInterfaceBusIdList().size() == 1);
  		assertTrue("SubArea (2) should have 2 buses", proc.getSubAreaNet(2).getSubNet().getBusList().size() == 2);

  		assertTrue("Bus2 should be in the SubArea (2)", dsNet.getBus("Bus2").getSubAreaFlag() == 2);
  		assertTrue("Bus7 should be in the SubArea (2)", dsNet.getBus("Bus7").getSubAreaFlag() == 2);
	}
	
	private BaseDStabNetwork getTestNet() {
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return null;
		}
		
	    return simuCtx.getDStabilityNet();
	}
}
