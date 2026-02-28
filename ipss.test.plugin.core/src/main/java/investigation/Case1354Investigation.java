package investigation;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfOutageBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;


public class Case1354Investigation {
	public static void main(String[] args) throws InterpssException {
		IpssCorePlugin.init();	

		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF, IpssFileAdapter.Version.IeeeCDFExt1)
				.load("testData/adpter/ieee_format/case1354_prefault.ieee")
				.getAclfNet();	
		
		aclfNet.createAclfBranchNameLookupTable(false);
		
		ContingencyAnalysisAlgorithm caAlgo = createContingencyAnalysisAlgorithm(aclfNet);
		caAlgo.calculateDclf();
		
		DclfAlgoBranch branchMonitor = caAlgo.getDclfAlgoBranch(aclfNet.getAclfBranchNameLookupTable().get("Branch434").getId());
		System.out.println("Pre-outage flow: " + branchMonitor.getDclfFlow() * aclfNet.getBaseMva());	
		
		DclfAlgoBranch branchOut = caAlgo.getDclfAlgoBranch(aclfNet.getAclfBranchNameLookupTable().get("Branch76").getId());
		DclfOutageBranch outageBranch = DclfAlgoObjectFactory.createCaOutageBranch(branchOut, ContingencyBranchOutageType.OPEN);
        double preFlow = outageBranch.getDclfFlow();
        
        double f = caAlgo.lineOutageDFactor(outageBranch, branchMonitor.getBranch());
        double postFlow = branchMonitor.getDclfFlow() + f * preFlow;
		System.out.println("Post-outage flow: " + postFlow * aclfNet.getBaseMva());	
	}
}
