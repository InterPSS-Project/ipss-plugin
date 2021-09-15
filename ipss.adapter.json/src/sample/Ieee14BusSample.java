package sample;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.datamodel.bean.DefaultExtBean;
import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.datamodel.mapper.aclf.AclfNet2AclfBeanMapper;
import org.interpss.fadapter.IpssFileAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;

public class Ieee14BusSample {

	public static void main(String[] args) throws InterpssException {
		IpssCorePlugin.init();
		
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		// map AclfNet to AclfNetBean
		AclfNetBean<DefaultExtBean> netBean = new AclfNet2AclfBeanMapper<DefaultExtBean>().map2Model(aclfNet);	
		System.out.println(netBean.toString());
	}
}
