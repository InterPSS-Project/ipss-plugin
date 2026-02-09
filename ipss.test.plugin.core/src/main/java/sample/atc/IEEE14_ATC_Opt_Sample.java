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
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.plugin.optadj.algo.AclfNetATCOptimizer;
import org.interpss.plugin.optadj.optimizer.ATCOptimizer;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.BranchOutageContingency;
import com.interpss.core.aclf.contingency.dclf.CaBranchOutageType;
import com.interpss.core.aclf.contingency.dclf.CaOutageBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoGen;
import com.interpss.core.algo.dclf.adapter.DclfAlgoLoad;

public class IEEE14_ATC_Opt_Sample {
	static AtomicCounter cnt = new AtomicCounter();
	
	public static void main(String args[]) throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();

		Set<String> sourceGenSet = new HashSet<>(Arrays.asList("Bus2-G1"));
		Set<String> sinkLoadSet = new HashSet<>(Arrays.asList("Bus10-L1", "Bus11-L1", "Bus12-L1", "Bus13-L1", "Bus14-L1"));
		Set<String> monitorBranchSet = new HashSet<>(Arrays.asList("Bus4->Bus7(1)", "Bus4->Bus9(1)", "Bus5->Bus6(1)"));

		// set the branch rating.
		net.getBranchList().stream().forEach(branch -> {
			AclfBranch aclfBranch = (AclfBranch) branch;
			if (monitorBranchSet.contains(aclfBranch.getId())) {
				// set lower rating for monitored branches
				aclfBranch.setRatingMva1(60.0);
				aclfBranch.setRatingMva2(80.0);
			} else if (aclfBranch.getId().equals("Bus1->Bus2(1)")) {
				aclfBranch.setRatingMva1(150.0);
				aclfBranch.setRatingMva2(160.0);
			} else {
				aclfBranch.setRatingMva1(120.0);
				aclfBranch.setRatingMva2(160.0);
			}
		});
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();

		// define a contingency list
		List<BranchOutageContingency> contList = new ArrayList<>();
		net.getBranchList().stream()
				// make sure the branch is not connected to a reference bus.
				.filter(branch -> !((AclfBranch) branch).isConnect2RefBus()).forEach(branch -> {
					// create a contingency object for the branch outage analysis
					BranchOutageContingency cont = createContingency("contBranch:" + branch.getId());
					// create an open CA outage branch object for the branch outage analysis
					CaOutageBranch outage = createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(branch.getId()),
							CaBranchOutageType.OPEN);
					cont.setOutageBranch(outage);
					contList.add(cont);
				});
		
		// check base case branch flow violations
		cnt = new AtomicCounter();
		dclfAlgo.getDclfAlgoBranchList().stream()
				.forEach(branch -> {
					double flowMw = branch.getDclfFlow() * net.getBaseMva();
					double loading = 100.0 * flowMw / branch.getBranch().getRatingMva1();
					if (loading >= 100.0) {
						cnt.increment();
						System.out.println(branch.getBranch().getId() + " loading: " + loading);
					}
				});
		System.out.println("Basecase Total number of branches with loading > 100%: " + cnt.getCount());
		
		cnt = new AtomicCounter();
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
		System.out.println("N-1 Total number of branches with loading > 100%: " + cnt.getCount());
		
		/*
		 * Perform ATC optimization
		 */
		AclfNetATCOptimizer op = new AclfNetATCOptimizer(dclfAlgo, new ATCOptimizer());
		op.setControlGenSet(sourceGenSet);
		
		op.setControlLoadSet(sinkLoadSet);
		//op.setOptimizer(new ATCOptimizer());
		
		op.optimize(100, monitorBranchSet);
		
		// after optimization, recalculate the DCLF with adjusted generation and load
		dclfAlgo.calculateDclf();
		
		// check base case branch flow violations
		cnt = new AtomicCounter();
		dclfAlgo.getDclfAlgoBranchList().stream()
				.forEach(branch -> {
					double flowMw = branch.getDclfFlow() * net.getBaseMva();
					double loading = 100.0 * flowMw / branch.getBranch().getRatingMva1();
					if (loading >= 100.0) {
						cnt.increment();
						System.out.println(branch.getBranch().getId() + " loading: " + loading);
					}
					else if (loading >= 90.0) {
						System.out.println(branch.getBranch().getId() + " loading(90%): " + loading);
					}
				});
		System.out.println("Basecase Total number of branches with loading > 100%: " + cnt.getCount());
		
		cnt = new AtomicCounter();
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
				else if (loading >= 90.0) {
					System.out.println(branch.getBranch().getId() + " loading(90%): " + loading);
				}
			});
		System.out.println("N-1 Total number of branches with loading > 100%: " + cnt.getCount());
		System.out.println(op.getResultMap());
		
		AclfGen gen = net.getBus("Bus2").getContributeGen("Bus2-G1");
		DclfAlgoGen dcGen = dclfAlgo.getDclfAlgoBus(gen.getParentBus().getId()).getGen(gen.getId()).get();
		System.out.println("Bus2-G1, new p :" + dcGen.getGenP() + " , origin p :" + gen.getGen().getReal());
		
		
		AclfLoad load = net.getBus("Bus10").getContributeLoad("Bus10-L1");
		DclfAlgoLoad dcLoad = dclfAlgo.getDclfAlgoBus(load.getParentBus().getId()).getLoad(load.getId()).get();
		System.out.println("Bus10-L1, new p :" + dcLoad.getLoadP() + " , origin p :" + load.getLoadCP().getReal());
		
		AclfLoad load11 = net.getBus("Bus11").getContributeLoad("Bus11-L1");
		DclfAlgoLoad dcLoad11 = dclfAlgo.getDclfAlgoBus(load11.getParentBus().getId()).getLoad(load11.getId()).get();
		System.out.println("Bus11-L1, new p :" + dcLoad11.getLoadP() + " , origin p :" + load11.getLoadCP().getReal());
		
		AclfLoad load14 = net.getBus("Bus14").getContributeLoad("Bus14-L1");
		DclfAlgoLoad dcLoad14 = dclfAlgo.getDclfAlgoBus(load14.getParentBus().getId()).getLoad(load14.getId()).get();
		System.out.println("Bus14-L1, new p :" + dcLoad14.getLoadP() + " , origin p :" + load14.getLoadCP().getReal());
	}
}

