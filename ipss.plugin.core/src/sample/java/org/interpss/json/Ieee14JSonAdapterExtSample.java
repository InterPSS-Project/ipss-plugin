package org.interpss.json;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.dep.datamodel.bean.aclf.AclfBranchBean;
import org.interpss.dep.datamodel.bean.aclf.AclfBusBean;
import org.interpss.dep.datamodel.bean.aclf.BaseAclfNetBean;
import org.interpss.dep.datamodel.bean.base.BaseJSONUtilBean;
import org.interpss.dep.datamodel.mapper.base.BaseAclfBean2AclfNetMapper;
import org.interpss.dep.datamodel.mapper.base.BaseAclfNet2AclfBeanMapper;
import org.interpss.fadapter.IpssFileAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

class MyBusExtBean extends BaseJSONUtilBean {
	public MyBusExtBean() { }
}

class MyBranchExtBean extends BaseJSONUtilBean {
	public MyBranchExtBean() { }
}

class MyNetExtBean extends BaseJSONUtilBean {
	public MyNetExtBean() { }
}

class MyAclfNetBean extends BaseAclfNetBean<
								AclfBusBean<MyBusExtBean>, 
								AclfBranchBean<MyBranchExtBean>, 
                                MyBusExtBean, MyBranchExtBean, MyNetExtBean> {
	public MyAclfNetBean() { 
		super(); 
	}
}

class MyAclfBean2AclfNetMapper extends BaseAclfBean2AclfNetMapper<MyBusExtBean, MyBranchExtBean, MyNetExtBean> {
	public MyAclfBean2AclfNetMapper() {
	}
}

class MyAclfNet2AclfBeanMapper extends BaseAclfNet2AclfBeanMapper<MyBusExtBean, MyBranchExtBean, MyNetExtBean> {
	public MyAclfNet2AclfBeanMapper() {
	}
	
	@Override public MyAclfNetBean map2Model(AclfNetwork aclfNet) throws InterpssException {
		MyAclfNetBean bean = new MyAclfNetBean();
		super.map2Model(aclfNet, bean);
		return bean;
	}	
}

public class Ieee14JSonAdapterExtSample {
	public static void main(String[] args) throws InterpssException {
		IpssCorePlugin.init();
		
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		// map AclfNet to AclfNetBean
		MyAclfNetBean netBean = new MyAclfNet2AclfBeanMapper()
				       				.map2Model(aclfNet);	
		System.out.println(netBean.toString());
		
		// map AclfNetBean back to an AclfNet object
		aclfNet = new MyAclfBean2AclfNetMapper()
						.map2Model(netBean)
						.getAclfNet();
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);
	  	algo.loadflow();
  		System.out.println(aclfNet.maxMismatch(AclfMethodType.NR));
  		//System.out.println(net.net2String());
	}
}
