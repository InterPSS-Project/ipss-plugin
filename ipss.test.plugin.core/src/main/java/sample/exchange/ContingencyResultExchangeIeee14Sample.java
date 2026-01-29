package sample.exchange;

import org.interpss.CorePluginFactory;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.exchange.ContingencyResultAdapter;
import org.interpss.plugin.exchange.ContingencyResultContainer;
import org.interpss.plugin.exchange.bean.ContingencyExchangeInfo;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class ContingencyResultExchangeIeee14Sample {
	public static void main(String[] args) throws InterpssException {
		//IpssCorePlugin.init();
		
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		ContingencyResultContainer container = new ContingencyResultContainer();
		
		/*
		 * Base case loadflow
		 */
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);

		aclfAlgo.loadflow();
		System.out.println("MaxMismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
		String[] busIds = aclfNet.getBusList().stream()
				.map(b -> b.getId())
				.toArray(String[]::new);
		
		String[] branchIds = aclfNet.getBranchList().stream()
				.map(b -> b.getId())
				.toArray(String[]::new);
		
		// store the base case result
		ContingencyExchangeInfo basecaseInfoBean = new ContingencyResultAdapter(aclfNet, "BaseCase", null)
				.createInfoBean(busIds, branchIds);
		
		container.getContingencyResultMap().put("BaseCase", basecaseInfoBean);
		
		 // retrieve the base case result as contingency result info bean
		ContingencyExchangeInfo netInfoBean = container.getContingencyResultMap().get("BaseCase");
		 // get bus results
		double[] voltMag = netInfoBean.busResultBean.volt_mag;
		double[] voltAng = netInfoBean.busResultBean.volt_ang;
		
		// get branch results
		double[] pF2T = netInfoBean.branchResultBean.p_f2t;
		double[] qF2T = netInfoBean.branchResultBean.q_f2t;
		double[] pT2F = netInfoBean.branchResultBean.p_t2f;
		double[] qT2F = netInfoBean.branchResultBean.q_t2f;
		
		/*
		 * outage case loadflow
		 */
		String outageBranchId = "Bus4->Bus7(1)";
		AclfNetwork continNet = aclfNet.jsonCopy();
		
		AclfBranch outageBranch = continNet.getBranch(outageBranchId);
		outageBranch.setStatus(false);
		
		aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(continNet);

		aclfAlgo.loadflow();
		System.out.println("MaxMismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
		 // store the contingency case result
		ContingencyExchangeInfo continInfoBean = 
				new ContingencyResultAdapter(aclfNet, outageBranchId, outageBranch)
					.createInfoBean(busIds, branchIds);
		
		container.getContingencyResultMap().put(outageBranchId, continInfoBean);
		
		// retrieve the contingency result as contingency result info bean
		netInfoBean = container.getContingencyResultMap().get(outageBranchId);
		// get bus results
		voltMag = netInfoBean.busResultBean.volt_mag;
		voltAng = netInfoBean.busResultBean.volt_ang;
		
		// get branch results
		pF2T = netInfoBean.branchResultBean.p_f2t;
		qF2T = netInfoBean.branchResultBean.q_f2t;
		pT2F = netInfoBean.branchResultBean.p_t2f;
		qT2F = netInfoBean.branchResultBean.q_t2f;
	}
}
