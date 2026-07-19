package org.interpss.core.adapter.psse.raw.aclf;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.dep.datamodel.bean.aclf.AclfNetBean;
import org.interpss.dep.datamodel.mapper.aclf.AclfNet2AclfBeanMapper;
import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class PSSE_Savnw_v33_Test extends CorePluginTestSetup {
	@Test
	public void testDataInputAndPowerflow() throws Exception {
		AclfNetwork net = new PSSEDirectParser(33).parse("testData/psse/v33/PSSE_sample_savnw.raw");
		
		//System.out.println(net.net2String());
		
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	//algo.setLfMethod(AclfMethod.PQ);
		algo.setNonDivergent(true);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	
	  	algo.loadflow();
  	

		assertTrue(net.isLfConverged());
		
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
	}
	
	@Test
	@Disabled("Requires missing fixture testData/adpter/psse/v30/savnw_v30.raw")
	public void compare() throws Exception {
		// load the test data V30
		AclfNetwork net30 = IpssAdapter.importAclfNet("testData/adpter/psse/v30/savnw_v30.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_30)
				.load()
				.getImportedObj();
		AclfNetBean netBean30 = new AclfNet2AclfBeanMapper().map2Model(net30);
		
		// load the test data V33
		AclfNetwork net33 = IpssAdapter.importAclfNet("testData/psse/v33/PSSE_sample_savnw.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_33)
				.load()
				.getImportedObj();
		AclfNetBean netBean33 = new AclfNet2AclfBeanMapper().map2Model(net33);
		
		// compare the data model with V30
		netBean30.compareTo(netBean33);		
	}

}
