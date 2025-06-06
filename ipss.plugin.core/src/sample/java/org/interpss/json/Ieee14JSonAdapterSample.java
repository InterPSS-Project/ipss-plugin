package org.interpss.json;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.dep.datamodel.bean.aclf.AclfNetBean;
import org.interpss.dep.datamodel.mapper.aclf.AclfBean2AclfNetMapper;
import org.interpss.dep.datamodel.mapper.aclf.AclfNet2AclfBeanMapper;
import org.interpss.fadapter.IpssFileAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class Ieee14JSonAdapterSample {
	public static void main(String[] args) throws InterpssException {
		IpssCorePlugin.init();
		
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper()
				       				.map2Model(aclfNet);	
		System.out.println(netBean.toString());
		
		// map AclfNetBean back to an AclfNet object
		aclfNet = new AclfBean2AclfNetMapper()
						.map2Model(netBean)
						.getAclfNet();
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(aclfNet);
	  	algo.loadflow();
  		System.out.println(aclfNet.maxMismatch(AclfMethodType.NR));
  		//System.out.println(net.net2String());
	}
}
