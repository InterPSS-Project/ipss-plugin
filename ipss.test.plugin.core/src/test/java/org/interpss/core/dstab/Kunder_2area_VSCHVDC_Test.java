package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.simu.SimuContext;

public class Kunder_2area_VSCHVDC_Test  extends DStabTestSetupBase{
	
	
	@Test
	public void test_Kunder_VSCHVDC_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		SimuContext simuCtx = new PSSEMultiFileLoader(30).loadDStab(
				"testData/adpter/psse/v30/Kundur_2area/Kundur_2area_v30.raw",
				"testData/adpter/psse/v30/Kundur_2area/kundur_2area.dyr");
		
	    BaseDStabNetwork dsNet =simuCtx.getDStabilityNet();
	    
	    //addVSCHVDC2Net
	    
	}
	
	private void addVSCHVDC2Net(BaseDStabNetwork dsNet, String fromBusId, String toBusId){
		
	}
	

}
