 /*
  * @(#)AclfNetPoolBookmarkRollback.java   
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
  * @Date 01/15/2013
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.pssl.util.impl;

import org.interpss.pssl.util.IAclfNetPoolHelper;

import com.interpss.core.aclf.AclfNetwork;

/**
 * AclfNet pool helper interface implementation with bookmark/rollback
 * 
 * @author mzhou
 *
 */
public class AclfNetPoolBookmarkRollback implements IAclfNetPoolHelper {
	/**
	 * initialize the net object before being borrowed from the pool. It
	 * is bookmarked.
	 * 
	 * @param net
	 */
	@Override public void init(AclfNetwork net) { 
		net.bookmark(true); // TODO implement ChangeRecorder
	}
	
	/**
	 * reset the net object before returning to the pool. It is 
	 * rolled back.
	 * 
	 * @param net
	 */
	@Override public void reset(AclfNetwork net) { 
		net.rollback();    // TODO implement ChangeRecorder
	}
}
