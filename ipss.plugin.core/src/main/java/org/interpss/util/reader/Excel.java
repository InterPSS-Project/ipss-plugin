 /*
  * @(#)Excel.java   
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
  * @Date 01/20/2011
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.util.reader;

import org.apache.poi.ss.usermodel.Cell;
import org.interpss.numeric.util.DateTimeUtil;


public class Excel {
	/**
	 * convert the int cell to a string
	 * 
	 * @param cell
	 * @return
	 */
	public static String int2Str(Cell cell) {
		return new Integer((int)cell.getNumericCellValue()).toString();
	}

	/**
	 * convert the date cell to a string, format "8/18/2011"
	 * 
	 * @param cell
	 * @return
	 */
	public static String date2Str(Cell cell) {
		return DateTimeUtil.formatDate(cell.getDateCellValue());
	}

	/**
	 * get value of an int cell
	 * 
	 * @param cell
	 * @return
	 */
	public static int getInt(Cell cell) {
		return (int)cell.getNumericCellValue();
	}

	/**
	 * get value of an int cell
	 * 
	 * @param cell
	 * @param defaultValue 
	 * @return
	 */
	public static int getInt(Cell cell, int defaultValue) {
		if (cell.toString().trim().equals(""))
			return defaultValue;
		return (int)cell.getNumericCellValue();
	}

	/**
	 * get value of an String cell
	 * 
	 * @param cell
	 * @return
	 */
	public static String getStr(Cell cell) {
		return cell.getStringCellValue();
	}

	/**
	 * get value of an double cell
	 * 
	 * @param cell
	 * @return
	 */
	public static double getDbl(Cell cell) {
		return cell.getNumericCellValue();
	}
}
