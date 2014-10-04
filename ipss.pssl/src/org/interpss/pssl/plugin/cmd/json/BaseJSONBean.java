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

import org.interpss.util.FileUtil;

import com.google.gson.Gson;

/**
 * Base bean class. The bean data model is intended for use in combination
 * with JSON. 
 * 
 * @author mzhou
 *
 */
public abstract class BaseJSONBean {
	/**
	 * Load the content in the file and convert into a JSon bean of type T
	 * 
	 * @param filename file name
	 * @param klass T class type, for example, AclfRunConfigBean.class
	 * @return
	 * @throws IOException
	 */
	public static <T> T toBean(String filename, Class<T> klass) throws IOException {
		FileUtil.readFile(new File(filename));
		String str = new String(FileUtil.readFile(new File(filename)));
			// sample: "{'lfMethod':'NR','maxIteration':20,'tolerance':1.0E-4,'nonDivergent':false,'initBusVoltage':false,'accFactor':1.0}";
		return (T)new Gson().fromJson(str, klass);
	}

	@Override
	public String toString() {
		return new Gson().toJson(this);
	}
 }
