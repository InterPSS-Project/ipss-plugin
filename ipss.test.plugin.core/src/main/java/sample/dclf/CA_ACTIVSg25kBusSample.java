package sample.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import java.util.ArrayList;
import java.util.List;

import org.interpss.IpssCorePlugin;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.algo.dclf.CaBranchOutageType;
import com.interpss.core.algo.dclf.CaOutageBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

public class CA_ACTIVSg25kBusSample {
	public static void main(String args[]) throws InterpssException {
		IpssCorePlugin.init();
		
		// load the test data V33
		AclfNetwork aclfNet = IpssAdapter.importAclfNet("testData/psse/v33/ACTIVSg25k.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();	
		
		// set the branch rating, since the original data does not have branch rating
		double branchLimit = 2500.0;
		aclfNet.getBranchList().stream()
			.filter(branch -> branch.isActive() && branch.isLine())
			.forEach(branch -> {
				AclfBranch aclfBranch = (AclfBranch) branch;
				aclfBranch.setRatingMva1(branchLimit);
			});	
	
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(aclfNet);
		dclfAlgo.calculateDclf();
	
		// define a contingency list
		List<Contingency> contList = new ArrayList<>();
		aclfNet.getBranchList().stream()
			// make sure the branch is not connected to a reference bus.
			.filter(branch -> branch.isActive() && branch.isLine() && 
								!branch.isConnect2RefBus() &&
								// only Aclf lines 230kv and above are selected to make sure that
								// there is no islanding scenario in the contingency analysis
								branch.getHigherBaseVoltage()>= 230000.0)
			.forEach(branch -> {
				// create a contingency object for the branch outage analysis
				Contingency cont = createContingency("contBranch:"+branch.getId());
				// create an open CA outage branch object for the branch outage analysis
				CaOutageBranch outage = createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(branch.getId()), 
								CaBranchOutageType.OPEN);
				cont.setOutageBranch(outage);
				contList.add(cont);
			});
		System.out.println("Contingency list size: " + contList.size());
		
		PerformanceTimer timer = new PerformanceTimer();
		contList.parallelStream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						//System.out.println(resultRec.aclfBranch.getId() + 
						//		", " + resultRec.contingency.getId() +
						//		" postContFlow: " + resultRec.getPostFlowMW());
						if (resultRec.calLoadingPercent() >= 100.0) {
							System.out.println(resultRec.aclfBranch.getId() + 
									", contBranch:" + resultRec.contingency.getId() +
									" postContFlow: " + resultRec.getPostFlowMW() +
									" loading: " + resultRec.calLoadingPercent());
						}
					});
			});
		timer.logStd("Contingency Analysis for ACTIVSg25kBusSample");
	}
}
