package sample.dclf.bus25k;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.IpssCorePlugin;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;

public class GSFParallel_ACTIVSg25kBusSample {
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

		String busId = "Bus11294"; // Gen@Bus-Bus11294
		
		aclfNet.getBranchList().stream()
			.filter(branch -> branch.isActive() && !branch.isConnect2RefBus() )
			.forEach(branch -> {
				double gsf = algo.calGenShiftFactor(busId, branch); // w.r.p to the Ref Bus
				if (Math.abs(gsf) > 0.2) 
					System.out.println("GSF Gen@" + busId + " on Branch " + branch.getId() + ": " + gsf);
			});
	}
}
/*
GSF Gen@Bus11294 on Branch Bus14265->Bus62125(1): 0.43305699304840073
GSF Gen@Bus11294 on Branch Bus11178->Bus11034(1): 0.21505304632561686
GSF Gen@Bus11294 on Branch Bus11034->Bus33349(1): 0.29826045384167155
GSF Gen@Bus11294 on Branch Bus11076->Bus11295(1): -0.24257306844923562
GSF Gen@Bus11294 on Branch Bus11174->Bus11178(1): 0.3034053363013301
GSF Gen@Bus11294 on Branch Bus11293->Bus11295(1): 0.9999999999999989
GSF Gen@Bus11294 on Branch Bus11293->Bus11294(1): -1.0
GSF Gen@Bus11294 on Branch Bus49362->Bus49031(1): -0.2671879595068673
*/
