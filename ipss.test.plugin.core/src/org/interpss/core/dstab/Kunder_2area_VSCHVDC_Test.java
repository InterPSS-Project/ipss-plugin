package org.interpss.core.dstab;

import static org.junit.Assert.assertTrue;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class Kunder_2area_VSCHVDC_Test  extends DStabTestSetupBase{
	
	
	@Test
	public void test_Kunder_VSCHVDC_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testdata/adpter/psse/v30/Kunder_2area/Kunder_2area_v30.raw",
				"testData/adpter/psse/v30/Kunder_2area/Kunder_2area.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());
        
		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
	    BaseDStabNetwork dsNet =simuCtx.getDStabilityNet();
	    
	    //addVSCHVDC2Net
	    
	}
	
	private void addVSCHVDC2Net(BaseDStabNetwork dsNet, String fromBusId, String toBusId){
		
	}
	
	

}
