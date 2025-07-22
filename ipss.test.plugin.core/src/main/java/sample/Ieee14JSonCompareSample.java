package sample;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.util.AclfNetJsonComparator;
import org.interpss.util.FileUtil;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.state.aclf.AclfNetworkState;

public class Ieee14JSonCompareSample {
	public static void main(String[] args) throws InterpssException {
		IpssCorePlugin.init();
		
		AclfNetwork aclfNet1 = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("ipss-plugin/ipss.test.plugin.core/testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		AclfNetwork aclfNet2 = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("ipss-plugin/ipss.test.plugin.core/testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	

		FileUtil.writeText2File("ipss-plugin/ipss.test.plugin.core/testdata/json/ieee14Bus.json", new AclfNetworkState(aclfNet1).toString());

		new AclfNetJsonComparator("Case1").compareJson(aclfNet1, aclfNet2);
		
		aclfNet2.getBus("Bus1").setBaseVoltage(132001.0);
		aclfNet2.getBranch("Bus1->Bus2(1)").setBranchCode(AclfBranchCode.BREAKER);
		
		new AclfNetJsonComparator("Case2").compareJson(aclfNet1, aclfNet2);
	}
}
