package org.interpss.core.ca;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.AtomicCounter;
import org.junit.jupiter.api.Test;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

public class IEEE14_N1Scan_Test extends CorePluginTestSetup {
	@Test
	public void test() throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		
		// set the branch rating.
		net.getBranchList().stream()
			.forEach(branch -> {
				AclfBranch aclfBranch = (AclfBranch) branch;
				aclfBranch.setRatingMva1(120.0);
			});
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();

		// define a contingency list
		List<DclfBranchOutage> contList = new ArrayList<>();
		net.getBranchList().stream()
			// make sure the branch is not connected to a reference bus.
			.filter(branch -> !((AclfBranch)branch).isConnect2RefBus())
			.forEach(branch -> {
				// create a contingency object for the branch outage analysis
				DclfBranchOutage cont = createContingency("contBranch:"+branch.getId());
				// create an open CA outage branch object for the branch outage analysis
				DclfOutageBranch outage = createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(branch.getId()), ContingencyBranchOutageType.OPEN);
				cont.setOutageEquip(outage);
				contList.add(cont);
			});
		
		AtomicCounter cnt = new AtomicCounter();
		contList.parallelStream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						//System.out.println(resultRec.aclfBranch.getId() + 
						//		", " + resultRec.contingency.getId() +
						//		" postContFlow: " + resultRec.getPostFlowMW());
						if (resultRec.aclfBranch.getId().equals("resultRec.aclfBranch.getId()") &&
								resultRec.contingency.getId().equals("contBranch:Bus4->Bus5(1)"))
							assertEquals(166.08, resultRec.getPostFlowMW(), 0.01);
						else if (resultRec.aclfBranch.getId().equals("resultRec.aclfBranch.getId()") &&
								resultRec.contingency.getId().equals("contBranch:Bus3->Bus4(1)"))
							assertEquals(152.90, resultRec.getPostFlowMW(), 0.01);
						
						if (resultRec.calLoadingPercent() >= 100.0) {
							cnt.increment();
							//System.out.println(resultRec.aclfBranch.getId() + 
							//		", contBranch:" + resultRec.contingency.getId() +
							//		" postContFlow: " + resultRec.getPostFlowMW() +
							//		" loading: " + resultRec.calLoadingPercent());
						}
					});
		});
		
		assertTrue(cnt.getCount() == 15, "Total number of branches with loading > 100%: " + cnt.getCount());
	}
}
/*
 * Bus1->Bus2(1), contBranch:Bus4->Bus5(1) postContFlow: 166.07624704742386
   Bus1->Bus2(1), contBranch:Bus3->Bus4(1) postContFlow: 152.89470786853144
*/
