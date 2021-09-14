/*
 * @(#)INetBeanComparator.java   
 *
 * Copyright (C) 2008-2019 www.interpss.org
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
 * @Date 12/10/2019
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.datamodel.util;

import java.util.Comparator;
import java.util.List;

/**
 * Interface to implement NetBean comparator 
 * 
 * @author mike zhou
 *
 * @param <T>
 */

public interface INetBeanComparator<T> extends Comparator<T> {
	public static enum CompareLog { Console, MsgList };

	/**
	 * get the Log message list
	 * 
	 * @return
	 */
	List<String> getMsgList();

}