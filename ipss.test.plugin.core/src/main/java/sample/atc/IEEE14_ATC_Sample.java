package sample.atc;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.interpss.CorePluginFactory;
import org.interpss.display.DclfOutFunc;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.AtomicCounter;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.algo.dclf.CaBranchOutageType;
import com.interpss.core.algo.dclf.CaOutageBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

public class IEEE14_ATC_Sample {
	
	public static void main(String args[]) throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();

		Set<String> sourceGenSet = new HashSet<>(Arrays.asList("Bus2-G1"));
		Set<String> sinkLoadSet = new HashSet<>(Arrays.asList("Bus10-L1", "Bus11-L1", "Bus12-L1", "Bus13-L1", "Bus14-L1"));
		Set<String> monitorBranchSet = new HashSet<>(Arrays.asList("Bus4->Bus7(1)", "Bus4->Bus9(1)", "Bus5->Bus6(1)"));

		// set the branch rating.
		net.getBranchList().stream()
			.forEach(branch -> {
				AclfBranch aclfBranch = (AclfBranch) branch;
				if (monitorBranchSet.contains(aclfBranch.getId()))  {
					// set lower rating for monitored branches
					aclfBranch.setRatingMva1(60.0);
					aclfBranch.setRatingMva2(80.0);
				}
				else  {
					aclfBranch.setRatingMva1(120.0);
					aclfBranch.setRatingMva2(160.0);
				}
			});
		
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
		
		double atc = 0.4; // MW
		dclfAlgo.getDclfAlgoBusList().stream()
			.forEach(dclfBus -> {
				dclfBus.getGenList().stream()
					.forEach(gen -> {
						if (sourceGenSet.contains(gen.getId())) {
							// distribute the generation increase evenly among the source generators
							gen.setAdjust(atc);
						}
					});
				dclfBus.getLoadList().stream()
					.forEach(load -> {
						if (sinkLoadSet.contains(load.getId())) {
							// distribute the load increase evenly among the sink loads
							load.setAdjust(0.2*atc);
						}
					});
			});
		
		dclfAlgo.calculateDclf();
		
		//System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false));
		
		// check base case branch flow violations
		AtomicCounter cnt = new AtomicCounter();
		dclfAlgo.getDclfAlgoBranchList().stream()
			.filter(branch -> monitorBranchSet.contains(branch.getBranch().getId()))
			.forEach(branch -> {
				double flowMw = branch.getDclfFlow() * net.getBaseMva();
				double loading = 100.0 * flowMw / branch.getBranch().getRatingMva1();
				if (loading >= 100.0) {
					cnt.increment();
					System.out.println(branch.getBranch().getId() + 
							" loading: " + loading);
				}
			});
		System.out.println("Basecase Total number of branches with loading > 100%: " + cnt.getCount());

		// define a contingency list
		List<Contingency> contList = new ArrayList<>();
		net.getBranchList().stream()
			// make sure the branch is not connected to a reference bus.
			.filter(branch -> !((AclfBranch)branch).isConnect2RefBus())
			.forEach(branch -> {
				// create a contingency object for the branch outage analysis
				Contingency cont = createContingency("contBranch:"+branch.getId());
				// create an open CA outage branch object for the branch outage analysis
				CaOutageBranch outage = createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(branch.getId()), CaBranchOutageType.OPEN);
				cont.setOutageBranch(outage);
				contList.add(cont);
			});
		
		AtomicCounter cnt1 = new AtomicCounter();
		contList.parallelStream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						//System.out.println(resultRec.aclfBranch.getId() + 
						//		", " + resultRec.contingency.getId() +
						//		" postContFlow: " + resultRec.getPostFlowMW());
						if (monitorBranchSet.contains(resultRec.aclfBranch.getId())) {
							double loading = resultRec.calLoadingPercent(resultRec.aclfBranch.getRatingMva2());
							if (loading >= 100.0) {
								cnt1.increment();
								System.out.println(resultRec.aclfBranch.getId() + 
										", contBranch:" + resultRec.contingency.getId() +
										" postContFlow: " + resultRec.getPostFlowMW() +
										" loading: " + loading);
							}
						}
					});
		});
		
		System.out.println("N-1 Total number of branches with loading > 100%: " + cnt1.getCount());
	}
}

