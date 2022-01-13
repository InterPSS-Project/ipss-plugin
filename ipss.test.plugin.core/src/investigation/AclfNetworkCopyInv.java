package investigation;

import org.interpss.IpssCorePlugin;
import org.interpss.util.FileUtil;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.simu.util.sample.SampleTestingCases;
import com.interpss.state.aclf.AclfNetworkState;

public class AclfNetworkCopyInv {

	public static void main(String[] args) {
		IpssCorePlugin.init();
		
  		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleTestingCases.load_LF_5BusSystem(net);

		AclfNetworkState netBean = new AclfNetworkState(net);
		FileUtil.writeText2File("temp/aclfnet.json",netBean.toString());
		
		AclfNetwork netCopy = net.hzCopy();
		AclfNetworkState netCopyBean = new AclfNetworkState(netCopy);
		FileUtil.writeText2File("temp/aclfnetcopy.json", netCopyBean.toString());
	}

}
