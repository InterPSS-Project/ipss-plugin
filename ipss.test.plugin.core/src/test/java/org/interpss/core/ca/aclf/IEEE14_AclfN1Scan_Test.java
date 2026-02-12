package org.interpss.core.ca.aclf;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.AclfContingencyObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.aclf.AclfBranchOutage;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfContingencyUtilFunc;
import com.interpss.core.funcImpl.compare.AclfNetObjectComparator;

public class IEEE14_AclfN1Scan_Test extends CorePluginTestSetup {
	@Test
	public void test() throws InterpssException {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		AclfNetwork baseNet = aclfNet.jsonCopy();
		
		// create contingency outage list
		List<AclfBranchOutage> branchOutageList = new ArrayList<>();
		aclfNet.getBranchList().stream()
			// exclude the branch outage, which will cause load flow non-convergence.
			.filter(branch -> !branch.getId().equals("Bus1->Bus2(1)"))
			.forEach(branch -> {
				AclfBranchOutage outage = AclfContingencyObjectFactory.createAclfBranchOutage(branch);
				branchOutageList.add(outage);
			});
		
		branchOutageList.forEach(outage -> {
			System.out.println("Outage branch: " + outage.getOutageEquip().getId());
			
			// for Aclf based contingency analysis, we need to make a copy of the original network 
			// model for each contingency scenario, and apply the outage to the network model copy.
			AclfNetwork netCopy = aclfNet.jsonCopy();
			AclfContingencyUtilFunc.applyBranchOutage(outage, netCopy);
			
			try {
				LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(netCopy);
				//aclfAlgo.setMaxIterations(50);
				//aclfAlgo.getNrMethodConfig().setNonDivergent(true);
				aclfAlgo.loadflow();
				
				assertTrue(netCopy.isLfConverged());
			} catch (InterpssException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
		
		// compare the network model with the base network model copy, 
		// to make sure the network model is not modified by the contingency analysis.
		AclfNetObjectComparator comp = new AclfNetObjectComparator(aclfNet, baseNet);
		comp.compareNetwork();
		
		assertTrue(comp.getDiffMsgList().isEmpty());
	}
}

