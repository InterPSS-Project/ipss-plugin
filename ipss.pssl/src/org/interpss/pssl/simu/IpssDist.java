 /*
  * @(#)IpssDist.java   
  *
  * Copyright (C) 2006 www.interpss.org
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

package org.interpss.pssl.simu;

import org.interpss.pssl.simu.net.IpssDistNet.DistNetDSL;

import com.interpss.dist.DistNetwork;

/**
 * DSL (domain specific language) for Distribution system analysis
 *  
 * @author mzhou
 *
 */
public class IpssDist {

	// ================ public methods =======================
	
	/**
	 * create DistNetwork analysis DSL
	 * 
	 * @param id
	 * @return
	 */
	public static DistNetDSL createDistNetwork(String id) {
		return new DistNetDSL(id);
	}
	
	/**
	 * wrap a DistNetwork object into a DistDSL object
	 * 
	 * @param net
	 * @return
	 */
	public static DistNetDSL wrapDistNetwork(DistNetwork net) {
		return new DistNetDSL(net);
	}
}
