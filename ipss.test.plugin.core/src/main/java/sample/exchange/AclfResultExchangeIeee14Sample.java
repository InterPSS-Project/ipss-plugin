package sample.exchange;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.exchange.AclfResultExchangeAdapter;
import org.interpss.plugin.exchange.bean.AclfNetExchangeInfo;

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
		
		String[] busIds = aclfNet.getBusList().stream()
				.map(b -> b.getId())
				.toArray(String[]::new);
		
		String[] branchIds = aclfNet.getBranchList().stream()
				.map(b -> b.getId())
				.toArray(String[]::new);
		
		AclfNetExchangeInfo netInfoBean = new AclfResultExchangeAdapter(aclfNet)
				.createNetInfoBean(busIds, branchIds);
		
		 // get bus results
		double[] voltMag = netInfoBean.busResultBean.volt_mag;
		double[] voltAng = netInfoBean.busResultBean.volt_ang;
		
		double[] pF2T = netInfoBean.branchResultBean.p_f2t;
		double[] qF2T = netInfoBean.branchResultBean.q_f2t;
		double[] pT2F = netInfoBean.branchResultBean.p_t2f;
		double[] qT2F = netInfoBean.branchResultBean.q_t2f;
	}
}
