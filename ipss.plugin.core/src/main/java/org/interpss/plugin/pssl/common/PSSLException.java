/*
 * @(#)PSSLException.java   
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

package org.interpss.plugin.pssl.common;

/**
 * InterPSS PSSL exception
 * 
 * @author mzhou
 *
 */
public class PSSLException extends Exception {
	private static final long serialVersionUID = 1;

	/**
	 * constructor
	 * 
	 * @param msg
	 */
	public PSSLException(String msg) {
		super(msg);
	}
}
