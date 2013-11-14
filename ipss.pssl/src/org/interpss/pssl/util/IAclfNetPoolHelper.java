 /*
  * @(#)IAclfNetPoolHelper.java   
  *
  * Copyright (C) 2008 www.interpss.org
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
  * @Date 12/15/2012
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.pssl.util;

import com.interpss.core.aclf.AclfNetwork;

/**
 * interface for AclfNetwork object pool helper functions
 * 
 * @author mzhou
 *
 */
public interface IAclfNetPoolHelper {
	/**
	 * initial the AclfNetwork object after the creation
	 * 
	 * @param net
	 */
	void init(AclfNetwork net);
	
	/**
	 * reset the AclfNetwork object before returning to the pool
	 * 
	 * @param net
	 */
	void reset(AclfNetwork net);
}
