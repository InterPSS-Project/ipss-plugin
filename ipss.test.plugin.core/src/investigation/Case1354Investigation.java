package investigation;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.dclf.CaBranchOutageType;
import com.interpss.core.algo.dclf.CaOutageBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.funcImpl.ZeroZBranchNetHelper;


public class Case1354Investigation {
	public static void main(String[] args) throws InterpssException {
		IpssCorePlugin.init();	

		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF, IpssFileAdapter.Version.IeeeCDFExt1)
				.load("testData/adpter/ieee_format/case1354π ’œ«∞.ieee")
				.getAclfNet();	
		
		aclfNet.createAclfBranchNameLookupTable(false);
		
		ContingencyAnalysisAlgorithm caAlgo = createContingencyAnalysisAlgorithm(aclfNet);
		caAlgo.calculateDclf();
		
		DclfAlgoBranch branchMonitor = caAlgo.getDclfAlgoBranch(aclfNet.getAclfBranchNameLookupTable().get("Branch434").getId());
		System.out.println("Pre-outage flow: " + branchMonitor.getDclfFlow() * aclfNet.getBaseMva());	
		
		DclfAlgoBranch branchOut = caAlgo.getDclfAlgoBranch(aclfNet.getAclfBranchNameLookupTable().get("Branch76").getId());
		CaOutageBranch outageBranch = DclfAlgoObjectFactory.createCaOutageBranch(branchOut, CaBranchOutageType.OPEN);
        double preFlow = outageBranch.getDclfFlow();
        
        double f = caAlgo.lineOutageDFactor(outageBranch, branchMonitor.getBranch());
        double postFlow = branchMonitor.getDclfFlow() + f * preFlow;
		System.out.println("Post-outage flow: " + postFlow * aclfNet.getBaseMva());	
	}
}
