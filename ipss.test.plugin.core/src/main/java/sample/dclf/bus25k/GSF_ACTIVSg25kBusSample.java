package sample.dclf.bus25k;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.IpssCorePlugin;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;

public class GSF_ACTIVSg25kBusSample {
	public static void main(String args[]) throws InterpssException {
		IpssCorePlugin.init();
		
		// load the test data V33
		AclfNetwork aclfNet = IpssAdapter.importAclfNet("ipss-plugin/ipss.test.plugin.core/testData/psse/v33/ACTIVSg25k.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();	
		
		aclfNet.getBusList().forEach(bus -> {
			if (bus.isGen()) {
				//System.out.println("GSF Gen@Bus-" + bus.getId());
			}
		});
		/*
		 * 	GSF Gen@Bus-Bus11294
			GSF Gen@Bus-Bus11299
			GSF Gen@Bus-Bus11303
		 */
		
		/*
		 * GFS samples
		 */
		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(aclfNet);

		String busId = "Bus11294"; // Gen@Bus-Bus11294
		/*
		aclfNet.getBranchList().stream()
			.filter(branch -> branch.isActive() && branch.isLine() && 
								!branch.isConnect2RefBus() &&
								branch.getHigherBaseVoltage() >= 230000.0)
			.forEach(branch -> {
				double gsf = algo.calGenShiftFactor(busId, branch); // w.r.p to the Ref Bus
				if (Math.abs(gsf) > 0.3) 
				System.out.println("GSF Gen@" + busId + " on Branch " + branch.getId() + ": " + gsf);
			});
		*/
		
		AclfBranch branch1 = aclfNet.getBranch("Bus14265->Bus62125(1)"); 
		double gsf1 = algo.calGenShiftFactor(busId, branch1); // w.r.p to the Ref Bus
		System.out.println("GSF Gen@" + busId + " on Branch " + branch1.getId() + ": " + gsf1);
	}
}
