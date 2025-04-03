package sample;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.util.NetJsonComparator;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfNetwork;

public class Ieee14JSonCompareSample {
	public static void main(String[] args) throws InterpssException {
		IpssCorePlugin.init();
		
		AclfNetwork aclfNet1 = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		AclfNetwork aclfNet2 = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	

		
		aclfNet2.getBus("Bus1").setBaseVoltage(132001.0);
		aclfNet2.getBranch("Bus1->Bus2(1)").setBranchCode(AclfBranchCode.BREAKER);
		
		NetJsonComparator.compareJson(aclfNet1, aclfNet2);
	}
}
