 /*
  * @(#)ExcelFileReader.java   
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

import static com.interpss.common.util.IpssLogger.ipssLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Iterator;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.interpss.util.IExcelFileProcessor;

import com.interpss.common.exp.InterpssException;


public class ExcelFileReader {
	protected String filepath = null;
	protected int indexSheet = 0;
	protected String sheetname = null;
	
	/**
	 * Constructor
	 * 
	 * @param filepath
	 * @param index spreadsheet index starting from 0
	 */
	public ExcelFileReader(String filepath, int index, String sheetname) {
		this.filepath = filepath;
		this.indexSheet = index;
		this.sheetname = sheetname;
	}
	
	/**
	 * Load the spreadsheet and process the row, one row at a time,
	 * using the File processor 
	 * 
	 * @param procer
	 * @return sheet name
	 */
	public String processFile(IExcelFileProcessor<Row> procer) throws InterpssException {
		final File file = new File(this.filepath);
	    Workbook wb;
		try {
			final InputStream stream = new FileInputStream(file);
	    	wb = WorkbookFactory.create(stream);
		} catch (Exception e) {
			ipssLogger.severe(e.toString());
			throw new InterpssException(e.toString());
		}
		if (wb.getNumberOfSheets() <= this.indexSheet)  // index starts from 0
			throw new InterpssException(this.sheetname + " does not exit in " + this.filepath);
	    Sheet sheet = wb.getSheetAt(this.indexSheet);
		Iterator<Row> rowIter = sheet.rowIterator();
		while (rowIter.hasNext()) {
			procer.processRow(rowIter.next());
		}
	    return sheet.getSheetName();
	}
}
