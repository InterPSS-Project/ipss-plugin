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

import com.interpss.CoreCommonFactory;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.common.util.IpssLogger;
  
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
}
