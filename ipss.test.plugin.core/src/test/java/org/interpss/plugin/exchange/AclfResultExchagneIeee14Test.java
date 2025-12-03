package org.interpss.plugin.exchange;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.exchange.bean.AclfNetExchangeInfo;
import org.junit.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class AclfResultExchagneIeee14Test extends CorePluginTestSetup {	
	@Test
	public void test() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);

		aclfAlgo.loadflow();
		
		assertTrue(aclfNet.isLfConverged());
		
		String[] busIds = aclfNet.getBusList().stream()
				.map(b -> b.getId())
				.toArray(String[]::new);
		
		String[] branchIds = aclfNet.getBranchList().stream()
				.map(b -> b.getId())
				.toArray(String[]::new);
		
		AclfNetExchangeInfo netInfoBean = new AclfResultExchangeAdapter(aclfNet)
				.createInfoBean(busIds, branchIds);
		
		 // get bus results
		double[] voltMag = netInfoBean.busResultBean.volt_mag;
		assertTrue(voltMag != null && voltMag.length == busIds.length);
		
		// get branch results
		double[] pF2T = netInfoBean.branchResultBean.p_f2t;
		assertTrue(pF2T != null && pF2T.length == branchIds.length);
	}
}

