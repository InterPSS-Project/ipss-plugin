package org.interpss.plugin.exchange;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.exchange.bean.ContingencyExchangeInfo;
import org.junit.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class ContingencyExchagneIeee14Test extends CorePluginTestSetup {	
	@Test
	public void test() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		ContingencyResultExContainer container = new ContingencyResultExContainer();
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);

		aclfAlgo.loadflow();
		
		assertTrue(aclfNet.isLfConverged());
		
		String[] busIds = aclfNet.getBusList().stream()
				.map(b -> b.getId())
				.toArray(String[]::new);
		
		String[] branchIds = aclfNet.getBranchList().stream()
				.map(b -> b.getId())
				.toArray(String[]::new);
		
		// store the base case result
		ContingencyExchangeInfo basecaseInfoBean = new ContingencyResultAdapter(aclfNet, "BaseCase", null)
				.createInfoBean(busIds, branchIds);
		assertTrue(basecaseInfoBean.lfConverged);
		
		container.getContingencyResultMap().put("BaseCase", basecaseInfoBean);
		
		 // retrieve the base case result as contingency result info bean
		ContingencyExchangeInfo netInfoBean = container.getContingencyResultMap().get("BaseCase");
		 // get bus results
		double[] voltMag = netInfoBean.busResultBean.volt_mag;
		assertTrue(voltMag != null && voltMag.length == busIds.length);
		
		// get branch results
		double[] pF2T = netInfoBean.branchResultBean.p_f2t;
		assertTrue(pF2T != null && pF2T.length == branchIds.length);
		
		/*
		 * outage case loadflow
		 */
		String outageBranchId = "Bus4->Bus7(1)";
		AclfNetwork continNet = aclfNet.jsonCopy();
		
		AclfBranch outageBranch = continNet.getBranch(outageBranchId);
		outageBranch.setStatus(false);
		
		aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(continNet);

		aclfAlgo.loadflow();
		System.out.println("MaxMismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
		 // store the contingency case result
		ContingencyExchangeInfo continInfoBean = 
				new ContingencyResultAdapter(aclfNet, outageBranchId, outageBranch)
					.createInfoBean(busIds, branchIds);
		assertTrue(continInfoBean.lfConverged);
		
		container.getContingencyResultMap().put(outageBranchId, continInfoBean);
		
		// retrieve the contingency result as contingency result info bean
		netInfoBean = container.getContingencyResultMap().get(outageBranchId);
		 // get bus results
		voltMag = netInfoBean.busResultBean.volt_mag;
		assertTrue(voltMag != null && voltMag.length == busIds.length);
		
		// get branch results
		pF2T = netInfoBean.branchResultBean.p_f2t;
		assertTrue(pF2T != null && pF2T.length == branchIds.length);
	}
}

