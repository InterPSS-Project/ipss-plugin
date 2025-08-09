package sample.dclf;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.DclfAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBus;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

public class Dclf_ACTIVSg25kBusSample {
	private static Double maxBranchFlow = 0.0;
	
	public static void main(String args[]) throws InterpssException {
		IpssCorePlugin.init();
		
		// load the test data V33
		AclfNetwork aclfNet = IpssAdapter.importAclfNet("testData/psse/v33/ACTIVSg25k.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();	
		
		DclfAlgorithm dclfAlgo = DclfAlgoObjectFactory.createDclfAlgorithm(aclfNet, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		//System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false));
		
		Set<String> baseVoltSet = new HashSet<>();
		Counter cnt = new Counter();
		dclfAlgo.getDclfAlgoBranchList().forEach(braDclf -> {
			AclfBranch braAclf = braDclf.getBranch();
			if (braAclf.isActive() && braAclf.isLine()) {
				double baseVoltKv = braAclf.getHigherBaseVoltage()*0.001;
				if (baseVoltKv >= 230.0) {
					cnt.increment();
				}
				String strVolt = new Double(baseVoltKv).toString();
				if (!baseVoltSet.contains(strVolt)) {
					baseVoltSet.add(strVolt);
				}
				//System.out.println("Branch: " + braAclf.getId() + ", " 
				//			+ baseVoltKv + ", "
				//			+ braDclf.getDclfFlow() * 100.0);
				
				if (maxBranchFlow < braDclf.getDclfFlow()) {
					maxBranchFlow = braDclf.getDclfFlow();
				}
			}
		});
		System.out.println("Base Voltages: " + baseVoltSet);	
		System.out.println("Number of branches with base voltage >= 230kV: " + cnt.getCount());
		System.out.println("Max branch flow: " + maxBranchFlow);

		DclfAlgoBus dclfBus = dclfAlgo.getDclfAlgoBus("Bus62120");
		//AclfBus bus1 = dclfBus.getBus();
		//int n1 = bus1.getSortNumber();
		double pgen = dclfAlgo.getBusPower(dclfBus) * aclfNet.getBaseMva(); 
		System.out.println("Bus62120 Pgen: " + pgen);		
	}
}
