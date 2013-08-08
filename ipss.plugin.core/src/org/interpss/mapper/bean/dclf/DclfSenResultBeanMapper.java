package org.interpss.mapper.bean.dclf;

import java.util.List;

import org.interpss.datamodel.bean.dclf.DclfBranchResultBean;
import org.interpss.datamodel.bean.dclf.DclfSenResultBean;
import org.interpss.datamodel.bean.dclf.GSFResultBean;

import com.interpss.core.aclf.AclfBranch;

public class DclfSenResultBeanMapper {
	
	/*public DclfSenResultBean mapGSFResult2Model(List<String> injBusList,
			List<String> withdrawBusList,List<AclfBranch> monBranchList, List<Double> gsfList){
		DclfSenResultBean dclfResult = new DclfSenResultBean();		
		int n = injBusList.size();
		for(int i = 0; i<n; i++	){
			String injBus = injBusList.get(i);
			String withdrawBus = withdrawBusList.get(i);
			AclfBranch monBra = monBranchList.get(i);
			double gsf = gsfList.get(i);
			
			GSFResultBean gsfBean = new GSFResultBean();
			dclfResult.gsf_list.add(gsfBean);
			gsfBean.injBus = injBus;
			gsfBean.withdrawBus = withdrawBus;
			gsfBean.gsf = gsf;
			
			DclfBranchResultBean bean = new DclfBranchResultBean();				
			bean.f_id = monBra.getFromBus().getId();
			bean.t_id = monBra.getToBus().getId();
			bean.cir_id = monBra.getCircuitNumber();			
			gsfBean.monBranch = bean;
		}
		
		
		return dclfResult;
	}
*/
}
