/*
 * @(#)IpssCorePlugin.java   
 *
 * Copyright (C) 2006-2010 www.interpss.org
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
 * @Date 12/04/2010
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import java.util.logging.Level;

import org.ieee.odm.common.ODMLogger;
import org.interpss.numeric.sparse.base.ISparseEquation;
import org.interpss.spring.NumericSpringFactory;

import com.interpss.CoreObjectFactory;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.algo.BusNumberArrangeRule;
import com.interpss.spring.CoreCommonSpringFactory;

/**
 * Core plugin runtime configuration functioin
 * 
 * @author mzhou
 *
 */
public class IpssCorePlugin {
	/**
	 * Core plugin Sptring ctx file path
	 */
	public final static String CtxPath = "org/interpss/spring/CorePluginSpringCtx.xml";
	
	/**
	 * initialize core plugin 
	 */
	public static void init() {
		init(Level.WARNING);
	}
	
	/**
	 * initialize core plugin 
	 * 
	 * @param paths array of Spring ctx paths
	 */
	public static void init(String[] paths) {
		init(paths, Level.WARNING);
	}

	/**
	 * initialize core plugin 
	 * 
	 * @param level log level
	 */
	public static void init(Level level) {
		init(new String[] {CtxPath}, level);
	}

	/**
	 * initialize core plugin 
	 * 
	 * @param paths array of Spring ctx paths
	 * @param level log level
	 */
	public static void init(String[] paths, Level level) {
		IpssLogger.initLogger();
		setSpringAppCtx(paths);
		setLoggerLevel(level);
	}

	/**
	 * set sparse eqn solver type. Native sparse eqn solver has been deprecated
	 * 
	 * @param solverType solver type
	 */
	@Deprecated
	public static void setSparseEqnSolver(ISparseEquation.SolverType solverType) {
		if (solverType == ISparseEquation.SolverType.Default ) {
			CoreObjectFactory.DefaultBusArrangeRule = BusNumberArrangeRule.TINNEY0;
			NumericSpringFactory.setDefualtSparseEqnSolver();
		}
		else if (solverType == ISparseEquation.SolverType.Native ) {
			CoreObjectFactory.DefaultBusArrangeRule = BusNumberArrangeRule.TINNEY0;
			NumericSpringFactory.setNativeSparseEqnSolver();
		}
	}
	
	/**
	 * get the MsgHub object
	 * 
	 * @return
	 */
	public static IPSSMsgHub getMsgHub() {
		return CoreCommonSpringFactory.getIpssMsgHub();
	}
	
	/**
	 * get logger level
	 * 
	 * @param level
	 */
	public static void setLoggerLevel(Level level) {
		ipssLogger.setLevel(level);
		ODMLogger.getLogger().setLevel(level);
	}	

	protected static void setSpringAppCtx(String[] paths) {
		CoreCommonSpringFactory.setAppContext(paths);
	}	
}
