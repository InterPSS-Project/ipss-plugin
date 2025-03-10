 /*
  * @(#)BaseDSL.java   
  *
  * Copyright (C) 2006-2011 www.interpss.com
  *
  * This program is free software; you can redistribute it and/or
  * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
  * as published by the Free Software Foundation; either version 2.1
  * of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * @Author Mike Zhou
  * @Version 1.0
  * @Date 04/15/2009
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.plugin.pssl.simu;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import java.util.logging.Level;

import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.common.msg.StdoutMsgListener;
import com.interpss.common.msg.impl.IPSSMsgHubImpl;
import com.interpss.common.msg.impl.TextMessageImpl;

/**
 * base class for defining InterPSS PSSL (DSL)
 * 
 * @author mzhou
 *
 */
public class BaseDSL {
	/**
	 * Analysis type exposed by PSSL and core simulation engine
	 */
	public static enum IpssAnalysisType {
		AclfAnalysis,
		AcscAnalysis,
		DStabAnalysis,
		DistAnalysis,
		DcSysAnalysis,
		DclfAnalysis,
		PowerTrainding,
		SensitivityAnalysis
	}
	
	/**
	 * Sparse eqn solver supportted
	 */
	public static enum SparseSolverType {
		IpssJava,
		Native,
	}

	/**
	 * PSSL msgHub object
	 */
	public static IPSSMsgHub psslMsg = null;

	/**
	 * sparse solver type
	 */
	public static SparseSolverType sparseSolver = SparseSolverType.IpssJava;

	/**
	 * set msgHub object
	 * 
	 * @param msg
	 */
	public static void setMsgHub(IPSSMsgHub msg) {
		psslMsg = msg;
	}
	
	/**
	 * Check if a particular analysis type is enabled for the PSSL and 
	 * core simulation engine 
	 * 
	 * @param type
	 * @return
	 */
	public static boolean analysisEnabled(IpssAnalysisType type) {
		if (type == IpssAnalysisType.PowerTrainding) {
			// TODO license check here
			return sparseSolver == SparseSolverType.Native;
		}
		return true;
	}
	
	static {
		ipssLogger.setLevel(Level.WARNING);
		psslMsg = new IPSSMsgHubImpl();
		psslMsg.addMsgListener(new StdoutMsgListener(TextMessageImpl.TYPE_WARN));
	}
}
