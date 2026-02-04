package sample.dclf.ei;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import java.util.HashSet;
import java.util.Set;

import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.DclfAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBus;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

public class Dclf_EastInterconnectionSample {
	public static void main(String args[]) throws InterpssException {
		// load the test data V33
		AclfNetwork aclfNet = IpssAdapter.importAclfNet("testData/psse/v33/Base_Eastern_Interconnect_515GW.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();	
		
		DclfAlgorithm dclfAlgo = DclfAlgoObjectFactory.createDclfAlgorithm(aclfNet, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		//System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false));
		
		Counter cnt = new Counter(0);
		dclfAlgo.getDclfAlgoBranchList().forEach(braDclf -> {
			AclfBranch branch = braDclf.getBranch();

            double powerFlowMW = dclfAlgo.getBranchFlow(branch, UnitType.mW);
            double ratingMVA = branch.getRatingMva1();
            double loadingPercent = ratingMVA > 0 ? (Math.abs(powerFlowMW) / ratingMVA) * 100.0 : 0.0;
            if ( loadingPercent > 100.0) {
            	System.out.println("Overloaded Branch: " + branch.getId() + ", Flow(MW): " + powerFlowMW + ", Rating(MVA): " + ratingMVA + ", Loading(%): " + loadingPercent);
            	cnt.increment();
            }
		});		
		System.out.println("Number of overloaded branches: " + cnt.getCount());
	}
}
