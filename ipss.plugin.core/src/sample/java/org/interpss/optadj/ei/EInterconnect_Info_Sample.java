package org.interpss.optadj.ei;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.core.aclf.AclfNetwork;

public class EInterconnect_Info_Sample {
	static class DblBuffer {
		double val;
	}
	
	static final String CASE_PATH = "ipss.plugin.core/testData/psse/v33/Base_Eastern_Interconnect_515GW.RAW";
	static final double OPT_THRESHOLD = 100.0;

    public static void main(String args[]) throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = loadCase();
		
		Counter zbrCnt = new Counter();
		Counter lineCnt = new Counter();
		Counter lineXSubCnt = new Counter();
		aclfNet.getBranchList().forEach(branch -> {
			if (branch.isZeroZBranch()) {
				zbrCnt.increment();
				System.out.println("Zero Impedance Branch: " + branch.getId() + 
						" From Bus: " + branch.getFromBus().getId() + 
						" To Bus: " + branch.getToBus().getId());
			}
			
			if (branch.isLine()) {
				lineCnt.increment();
			}
			
			if (branch.isLine() && branch.getFromAclfBus().getSubstationId().equals(branch.getToAclfBus().getSubstationId())) {
				lineXSubCnt.increment();
				if (lineXSubCnt.getCount() < 10) {
					System.out.println("Line connecting buses in the same substation: " + branch.getId() + 
							" From Sus: " + branch.getFromBus().getSubstation().getId() + 
							" To Sus: " + branch.getToBus().getSubstation().getId() +
							" Substation ID: " + branch.getFromAclfBus().getSubstationId());
				}
			}
			
		});
		System.out.println("Total number of Zero Impedance Branches: " + zbrCnt.getCount());
		System.out.println("Total number of Lines: " + lineCnt.getCount());
		System.out.println("Total number of Lines connecting buses in the same substation: " + lineXSubCnt.getCount());
		
		// set the generator Pgen limit
		aclfNet.createAclfGenNameLookupTable(false).forEach((k, gen) -> {
			//System.out.println("Adj Gen: " + gen.getName());
			if (gen.getPGenLimit() == null) {
				gen.setPGenLimit(new LimitType(5, 0));
			}
		});
	}

	static AclfNetwork loadCase() throws Exception {
		AclfNetwork aclfNet = IpssAdapter.importAclfNet(CASE_PATH)
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33)
				.load()
				.getImportedObj();
		aclfNet.createAclfGenNameLookupTable(false).forEach((k, gen) -> {
			if (gen.getPGenLimit() == null) {
				gen.setPGenLimit(new LimitType(5, 0));
			}
		});
		return aclfNet;
	}
}
