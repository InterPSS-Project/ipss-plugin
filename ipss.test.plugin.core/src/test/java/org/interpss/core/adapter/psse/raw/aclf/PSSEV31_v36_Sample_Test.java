package org.interpss.core.adapter.psse.raw.aclf;

import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.plugin.IpssAdapter.FileFormat;
import org.interpss.pssl.plugin.IpssAdapter.PsseVersion;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class PSSEV31_v36_Sample_Test extends CorePluginTestSetup{


	
	
@Test
public void testV31() throws Exception {
	AclfNetwork net = IpssAdapter.importAclfNet("testdata/psse/v31/sample_v31.raw")
			.setFormat(FileFormat.PSSE)
			.setPsseVersion(PsseVersion.PSSE_31)
			.load()
			.getImportedObj();
	
	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
  	//algo.setLfMethod(AclfMethod.PQ);
	//algo.setNonDivergent(true);
  	algo.setLfMethod(AclfMethodType.NR);
  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
  	
  	algo.loadflow();
	

	assertTrue(net.isLfConverged());
	
	System.out.println(AclfOutFunc.loadFlowSummary(net));
	
	checkData(net);

}

	
@Test
public void testV32() throws Exception {
	

}

@Test
public void testV33() throws Exception {
	

}

@Test
public void testV34() throws Exception {
	
}

@Test
public void testV35() throws Exception {


	
}

@Test
public void testV36() throws Exception {


	
}

private void checkData(AclfNetwork net) {
	
}

}



