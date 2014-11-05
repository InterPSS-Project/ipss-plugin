/*
 * @(#)BaseJSONBean.java   
 *
 * Copyright (C) 2008-2013 www.interpss.org
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
 * @Date 01/10/2013
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.pssl.plugin.cmd.json;

import java.io.File;
import java.io.IOException;

import org.interpss.pssl.plugin.cmd.IDslRunner;
import org.interpss.util.FileUtil;

import com.google.gson.Gson;
import com.interpss.common.exp.InterpssException;

/**
 * Base bean class. The bean data model is intended for use in combination
 * with JSON. 
 * 
 * @author mzhou
 *
 */
public abstract class BaseJSONBean {
	/**
	 *  Dsl runner class name for customization
	 */  
	public String dslRunnerClassName = null;
	
	/**
	 * Load the content in the file and convert into a JSon bean of type T
	 * 
	 * @param filename file name
	 * @param klass T class type, for example, AclfRunConfigBean.class
	 * @return the create JSON bean
	 * @throws IOException
	 */
	public static <T> T toBean(String filename, Class<T> klass) throws IOException {
		byte[] bAry = FileUtil.readFile(new File(filename));
		return (T)new Gson().fromJson(new String(bAry), klass);
	}
	
	/**
	 * Load DSL runner using the class name
	 * 
	 * @return DSL runner object
	 * @throws InterpssException
	 */
	public IDslRunner loadDslRunner() throws InterpssException {
		try {
			return (IDslRunner)Class.forName(dslRunnerClassName).newInstance();
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
			throw new InterpssException(e.toString());
		}
	}

	@Override
	public String toString() {
		return new Gson().toJson(this);
	}
 }
