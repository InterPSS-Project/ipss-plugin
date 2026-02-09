package sample.dclf.bus25k;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.IpssCorePlugin;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.dclf.CaBranchOutageType;
import com.interpss.core.aclf.contingency.dclf.CaOutageBranch;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;

public class LODF_ACTIVSg25kBusSample {
	public static void main(String args[]) throws InterpssException {
		IpssCorePlugin.init();
		
		// load the test data V33
		AclfNetwork aclfNet = IpssAdapter.importAclfNet("testData/psse/v33/ACTIVSg25k.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();	
		
		/*
		 * GFS samples
		 */
		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(aclfNet);
		algo.calculateDclf();
		
		String branchId = "Bus14265->Bus62125(1)"; 
		
		DclfAlgoBranch dclfBranch = algo.getDclfAlgoBranch(branchId);
		CaOutageBranch outBranch = DclfAlgoObjectFactory.createCaOutageBranch(dclfBranch, CaBranchOutageType.OPEN);	

		aclfNet.getBranchList().parallelStream()
			.filter(branch -> branch.isActive() && !branch.isConnect2RefBus() &&
								!branch.getId().equals(branchId))
			.forEach(branch -> {
				double lodf;
				try {
					lodf = algo.lineOutageDFactor(outBranch, branch);
					if (Math.abs(lodf) > 0.2) {
						System.out.printf("LODF for Branch %s on Outage Branch %s: %.4f%n", 
								branch.getId(), branchId, lodf);
					}
				} catch (InterpssException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
	}
}
