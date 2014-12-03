package org.interpss.pssl.plugin.cmd;

import java.io.IOException;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.interpss.pssl.plugin.IpssAdapter.FileImportDSL;
import org.interpss.pssl.plugin.cmd.json.BaseJSONBean;
import org.interpss.pssl.plugin.cmd.json.DstabRunConfigBean;
import org.interpss.util.FileUtil;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.net.Network;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.common.IDStabSimuOutputHandler;

public abstract class BaseDStabDslRunner implements IDslRunner {
	
	protected DStabilityNetwork net;
	protected DstabRunConfigBean dstabBean;

	@Override
	public IDslRunner setNetwork(Network<?, ?> net) {
		this.net = (DStabilityNetwork) net;
		return this;
	}
	
	@Override
	public BaseJSONBean loadConfigBean(String beanFileName)  throws IOException { 
		return BaseJSONBean.toBean(beanFileName, DstabRunConfigBean.class);
	}
	
	/**
	 * A common interface for all DSLRunner. Any DStab DSLRunner should override it.
	 * 
	 * @param dstabConfigBean
	 * @return
	 */
	protected abstract IDStabSimuOutputHandler runDstab (DstabRunConfigBean dstabConfigBean);

	@Override
	public <T> T run(BaseJSONBean bean) throws InterpssException {
        if(!(bean instanceof DstabRunConfigBean))
			try {
				throw new Exception("The input bean is not of DstabRunConfigBean type!");
			} catch (Exception e1) {
				
				e1.printStackTrace();
			}
		
        dstabBean = (DstabRunConfigBean) bean;
		
		FileImportDSL inDsl =  new FileImportDSL();
		inDsl.setFormat(dstabBean.acscConfigBean.runAclfConfig.format)
			 .setPsseVersion(dstabBean.acscConfigBean.runAclfConfig.version)
		     .load(NetType.DStabNet,new String[]{dstabBean.acscConfigBean.runAclfConfig.aclfCaseFileName,
		    		 dstabBean.acscConfigBean.seqFileName,
		    		 dstabBean.dynamicFileName});
		
		// map ODM to InterPSS model object
		try {
			net = inDsl.getImportedObj();
		} catch (InterpssException e) {
			e.printStackTrace();
			return (T)null;
		}	
					
		IDStabSimuOutputHandler outputHdler = runDstab(dstabBean);
		
		//output the result
        
		if(!dstabBean.dstabOutputFileName.equals("")){
			FileUtil.write2File(dstabBean.dstabOutputFileName, outputHdler.toString().getBytes());
			IpssLogger.getLogger().info("Ouput written to " + dstabBean.dstabOutputFileName);
		}
		

		return (T) outputHdler;
	}

}
