package org.interpss.core.mnet;

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.logging.Level;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.algo.SubNetworkProcessor;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.net.childnet.ChildNetwork;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestSubnetworkProcessor {
	
	@Test
	public void test_IEEE9Bus() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				//"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
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
	    
	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
	    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)");
	    proc.addSubNetInterfaceBranch("Bus7->Bus8(0)");
	    
	    proc.searchSubNetwork();
	    
	    List<ChildNetwork<? extends AclfBus, ? extends AclfBranch>> subNetList = proc.getSubNetworkList();
	    assertTrue(subNetList.size()==2);
	    System.out.println("Sub network -1");
	    for(Bus b:subNetList.get(0).getNetwork().getBusList() ){
	    	System.out.println("Bus:"+b.getId());
	    }
	    
	    for(Branch bra:subNetList.get(0).getNetwork().getBranchList()){
	    	System.out.println("Branch:"+bra.getId());
	    }
	    assertTrue(subNetList.get(0).getNetwork().getBranchList().size()==6);
	    
	    
	    System.out.println(dsNet.getChildNetList().get(0).getNetwork().net2String());
	    
	    System.out.println("Sub network -2");
	    for(Bus b:dsNet.getChildNetList().get(1).getNetwork().getBusList() ){
	    	System.out.println("Bus:"+b.getId());
	    	
	    }
	    assertTrue(dsNet.getChildNetList().get(1).getNetwork().getBranchList().size()==1);
	    
	    System.out.println(proc.getBusId2SubNetworkTable().toString());
	    
	    assertTrue(proc.getBusId2SubNetworkTable().get("Bus2")==1);
	    assertTrue(proc.getBusId2SubNetworkTable().get("Bus7")==1);
	    assertTrue(proc.getBusId2SubNetworkTable().get("Bus8")==0);
	}

}
