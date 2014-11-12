package org.interpss.pssl.plugin.cmd;

import java.io.IOException;

import org.interpss.pssl.plugin.cmd.json.BaseJSONBean;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.net.Network;

/**
 * DSL runner interface
 * 
 * @author Mike
 *
 */
public interface IDslRunner {
	/**
	 * network object setter
	 * 
	 * @param net Network object
	 */
	public abstract IDslRunner setNetwork(Network<?, ?> net);

	/**
	 * run analysis using the JSON case definition
	 * 
	 * @param bean
	 * @return
	 * @throws InterpssException 
	 */
	public abstract <T> T run(BaseJSONBean bean) throws InterpssException;
	
    /**
     * load configuration json bean
     * 
     * @param beanFileName
     * @return
     */
	public BaseJSONBean loadConfigBean(String beanFileName) throws IOException;
}