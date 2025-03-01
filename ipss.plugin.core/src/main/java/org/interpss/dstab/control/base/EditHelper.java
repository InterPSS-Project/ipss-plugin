/*
 * @(#)EditUtilFunct.java   
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
 * @Date 09/15/2006
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.dstab.control.base;

/**
 * Util functions for building controller editing screen
 * 
 */
import java.util.Vector;

import javax.swing.JTextField;

import org.interpss.numeric.util.Number2String;
import org.interpss.ui.SwingInputVerifyUtil;

public class EditHelper {
	/**
	 * Set the TextFeild (double) with the data in the format
	 * 
	 * @param textField a text field
	 * @param data the data
	 * @param format format of the data
	 */
	public static void setDblTextFiled(JTextField textField, double data, String format) {
		textField.setText(Number2String.toStr(data, format));
	}

	/**
	 * Set the TextFeild (int) with the data in the format
	 * 
	 * @param textField a text field
	 * @param data the data
	 */
	public static void setIntTextFiled(JTextField textField, int data) {
		textField.setText(Number2String.toStr(data));
	}

	/**
	 * Save the TextField value (double) to the controller data object under the
	 * dataName, for example, "ka"
	 * 
	 * @param data  controller data object
	 * @param textField  a screen text field
	 * @param dataName data filed name, for example, "ka"
	 * @param errMsg error message container in case the data is out of range
	 * @throws Exception
	 */
	public static void saveDblTextField(BaseControllerData data,
			JTextField textField, String dataName, Vector<String> errMsg)
			throws Exception {
		double max = data.getMaxValue(dataName);
		double min = data.getMinValue(dataName);
		double x = SwingInputVerifyUtil.getDouble(textField);
		if (SwingInputVerifyUtil.within(textField, min, max, errMsg, dataName
				+ "(" + x + ") is out of the range [" + max + "," + min + "]"))
			data.setValue(dataName, x);
	}

	/**
	 * Save the TextField value (int) to the controller data object under the
	 * dataName "ka"
	 * 
	 * @param data  controller data object
	 * @param textField  a screen text field
	 * @param dataName data filed name, for example, "ka"
	 * @param errMsg error message container in case the data is out of range
	 * @throws Exception
	 */
	public static void saveIntTextField(BaseControllerData data,
			JTextField textField, String dataName, Vector<String> errMsg)
			throws Exception {
		double max = data.getMaxValue(dataName);
		double min = data.getMinValue(dataName);
		int x = SwingInputVerifyUtil.getInt(textField);
		if (SwingInputVerifyUtil.within(textField, min, max, errMsg, dataName
				+ "(" + x + ") is out of the range [" + max + "," + min + "]"))
			data.setValue(dataName, x);
	}

	/**
	 * Check screen input (TextField) value (double) range violation
	 * 
	 * @param input screen input text field
	 * @param data  controller data object
	 * @param dataName  data field name, for example, "ka"
	 * @return true or false
	 * @throws Exception
	 */
	public static boolean checkDblDataRange(Object input,
			BaseControllerData data, String dataName) throws Exception {
		double x = SwingInputVerifyUtil
				.getDouble((javax.swing.JTextField) input);
		return !data.isDblOutRange(dataName, x);
	}

	/**
	 * Check screen input (TextField) value (double) range violation
	 * 
	 * @param input  screen input text field
	 * @param data  controller data object
	 * @param dataName  data field name, for example, "ka"
	 * @return true or false
	 * @throws Exception
	 */
	public static boolean checkIntDataRange(Object input,
			BaseControllerData data, String dataName) throws Exception {
		int x = SwingInputVerifyUtil.getInt((javax.swing.JTextField) input);
		return !data.isDblOutRange(dataName, x);
	}
}
