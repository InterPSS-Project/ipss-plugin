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

import com.interpss.common.CoreCommonFactory;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.sparse.SparseEqnObjectFactory;
import com.interpss.core.sparse.solver.SparseEqnSolverFactory;
import com.interpss.core.sparse.solver.SparseEqnSolverProvider;

import org.interpss.core.sparse.impl.klusolvex.KlusolveXSparseEqnComplexImpl;
import org.interpss.core.sparse.impl.klusolvex.KlusolveXSparseEqnComplexMatrix3x3Impl;
import org.interpss.core.sparse.solver.KlusolveXAvailability;
  
/**
 * Core plugin runtime configuration functioin
 * 
 * @author mzhou
 *
 */
@Deprecated
public class IpssCorePlugin {
	public enum SparseSolverType {
		CSJ,
		JAVA_KLU,
		KLUSOLVEX,
		KLUSOLVEX_AUTO
	}

	public record Selection(SparseSolverType requested, SparseSolverType active, String message) {
	}

	/**
	 * Core plugin Sptring ctx file path
	 */
	//public final static String CtxPath = "org/interpss/spring/CorePluginSpringCtx.xml";
	   
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
	 * @param level log level
	 */
	public static void init(Level level) {
		IpssLogger.initLogger();
		setLoggerLevel(level);
	}

	/**
	 * get the MsgHub object
	 * 
	 * @return
	 */
	public static IPSSMsgHub getMsgHub() {
		return CoreCommonFactory.getIpssMsgHub();
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

	public static Selection configureSparseSolverFromSystemProperties() {
		String solver = System.getProperty(SparseEqnSolverProvider.SOLVER_PROPERTY, "java-klu").trim().toLowerCase();
		switch(solver) {
		case "java-klu":
		case "java_klu":
		case "klu":
			SparseEqnSolverProvider.Selection javaKlu = SparseEqnSolverProvider.useJavaKlu();
			return new Selection(SparseSolverType.JAVA_KLU, SparseSolverType.JAVA_KLU, javaKlu.message());
		case "klusolvex":
			return useKlusolveX();
		case "auto":
		case "klusolvex-auto":
		case "klusolvex_auto":
			return useKlusolveXIfAvailable();
		case "csj":
		default:
			SparseEqnSolverProvider.Selection csj = SparseEqnSolverProvider.useCSJ();
			return new Selection(SparseSolverType.CSJ, SparseSolverType.CSJ, csj.message());
		}
	}

	public static Selection useKlusolveXIfAvailable() {
		if(KlusolveXAvailability.isNativeLibraryLoadable()) {
			return useKlusolveX();
		}
		SparseEqnSolverProvider.useCSJ();
		return new Selection(SparseSolverType.KLUSOLVEX_AUTO, SparseSolverType.CSJ,
				KlusolveXAvailability.unavailableReason() + "; falling back to CSJ");
	}

	public static Selection useKlusolveX() {
		if(!KlusolveXAvailability.isNativeLibraryLoadable()) {
			throw new IllegalStateException(KlusolveXAvailability.unavailableReason());
		}
		SparseEqnObjectFactory.setDoubleEqnCreator(null);
		SparseEqnObjectFactory.setMatrix2x2EqnCreator(null);
		SparseEqnObjectFactory.setComplexEqnCreator(KlusolveXSparseEqnComplexImpl::new);
		SparseEqnObjectFactory.setComplextMatrix3x3EqnCreator(KlusolveXSparseEqnComplexMatrix3x3Impl::new);
		SparseEqnSolverFactory.setDoubleSolverCreator(null);
		SparseEqnSolverFactory.setComplexSolverCreator(null);
		return new Selection(SparseSolverType.KLUSOLVEX, SparseSolverType.KLUSOLVEX,
				"Using KLUSolveX for complex sparse equations; real equations use CSJ");
	}
}
