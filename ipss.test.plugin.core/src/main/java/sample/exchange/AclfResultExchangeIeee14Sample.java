package sample.exchange;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.exchange.AclfResultExchangeAdapter;
import org.interpss.plugin.exchange.bean.AclfBranchExchangeInfo;
import org.interpss.plugin.exchange.bean.AclfBusExchangeInfo;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class AclfResultExchangeIeee14Sample {
	public static void main(String[] args) throws InterpssException {
		IpssCorePlugin.init();
		
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);

		aclfAlgo.loadflow();
		
		System.out.println("MaxMismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
		AclfResultExchangeAdapter adapter = new AclfResultExchangeAdapter(aclfNet);
		
		String[] busIds = aclfNet.getBusList().stream()
				.map(b -> b.getId())
				.toArray(String[]::new);
		
		String[] branchIds = aclfNet.getBranchList().stream()
				.map(b -> b.getId())
				.toArray(String[]::new);
		
		adapter.setBusIds(busIds);
		adapter.fillBusResult();
		double[] voltMag = adapter.getBusVoltMag();
		double[] voltAng = adapter.getBusVoltAng();
		
		adapter.setBranchIds(branchIds);
		adapter.fillBranchResult();
		double[] pF2T = adapter.getBranchPf2t();
		double[] qF2T = adapter.getBranchQf2t();
		double[] pT2F = adapter.getBranchPt2f();
		double[] qT2F = adapter.getBranchQt2f();
	}
}
