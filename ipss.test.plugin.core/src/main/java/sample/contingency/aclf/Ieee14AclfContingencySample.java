package sample.contingency.aclf;

import java.util.ArrayList;
import java.util.List;

import org.interpss.CorePluginFactory;
import org.interpss.fadapter.IpssFileAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.AclfContingencyObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.contingency.aclf.AclfBranchOutage;
import com.interpss.core.funcImpl.AclfContingencyUtilFunc;

public class Ieee14AclfContingencySample {
	public static void main(String[] args) throws InterpssException {
		//IpssCorePlugin.init();
		
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
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
				aclfAlgo.loadflow();
			} catch (InterpssException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
	}
}
