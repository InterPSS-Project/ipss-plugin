/*
 * @(#)ICustomPluginEditor.java   
 *
 * Copyright (C) 2006-2007 www.interpss.com
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
 * @Date 09/15/2006
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.ui;

import java.util.Vector;

/**
 * Custom editor plugin interface
 * 
 * @author mzhou
 *
 */
public interface ICustomPluginEditor {

	/**
	 * Init the editor with an plugin data object to be edited.
	 * 
	 * @param obj the plugin data object
	 */
	void init(Object obj);

	/**
	 * Set the plugin data to the editor screen
	 * 
	 * @param desc plugin description
	 * @return true if the set-up is successful
	 */
	boolean setData2Editor(String desc);

	/**
	 * Save the editor screen data back to the obj.Data object
	 * 
	 * @param errMsg if there are data error, error msgs are stored in the list
	 * @return false if there is a data error(s)
	 */
	boolean saveEditorData(Vector<String> errMsg) throws Exception;

} // ICustomPluginEditor
