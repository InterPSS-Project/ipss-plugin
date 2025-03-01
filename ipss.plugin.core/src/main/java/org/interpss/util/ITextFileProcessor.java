/*
 * @(#)ITextFileProcessor.java   
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
 * @Date 09/15/2011
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.util;

import com.interpss.common.exp.InterpssException;

/**
 * Text file line processor interface
 * 
 * @author mzhou
 *
 */
public interface ITextFileProcessor {
	/**
	 * for processing a line in, for example, text file
	 * 
	 * @param lineStr
	 * @return
	 * @throws InterpssException
	 */
	boolean processLine(String lineStr) throws InterpssException;
}
