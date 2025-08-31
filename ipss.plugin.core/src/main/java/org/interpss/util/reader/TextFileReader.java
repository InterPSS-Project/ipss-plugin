/*
  * @(#)TextFileReader.java   
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

import org.interpss.util.ITextFileProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * A text file reader implementation. The file is processed line by line
 * by using a file line processor. The processFile method takes the file
 * line processor and performs the line processing.
 * 
 * 
 * @author mzhou
 *
 */
public class TextFileReader {
    private static final Logger log = LoggerFactory.getLogger(TextFileReader.class);
	protected String filepath = null;

	/**
	 * default constructor
	 */
	public TextFileReader() {
	}

	/**
	 * constructor
	 * 
	 * @param filepath
	 */
	public TextFileReader(String filepath) {
		this.filepath = filepath;
	}
	
	/**
	 * set file full path including file name
	 * 
	 * @param filepath
	 */
	public void setFilepath(String filepath) {
		this.filepath = filepath;
	}

	/**
	 * process the text file line-by-line by the processor
	 * 
	 * @param procer
	 */
	public void processFile(ITextFileProcessor procer) {
		try {
			final File file = new File(this.filepath);
			final InputStream stream = new FileInputStream(file);
			String str = null;
			final BufferedReader din = new BufferedReader(new InputStreamReader(stream));		
			
			try {
				do {
					str = din.readLine(); 
					if (str != null)
						procer.processLine(str);
				} while (str != null);
			} catch (InterpssException e)  {
				log.error(e.toString());
			} finally {
				din.close();
			}
		} catch (Exception e) {
			log.error(e.toString());
		}
	}
	
	/**
	 * Get the file content as List<String>
	 * 
	 * @return the list
	 */
	public List<String> getFileContent() {
		TextFileProcessor proc = new TextFileProcessor();
		this.processFile(proc);
		return proc.strList;
	}
	
	private static class TextFileProcessor implements ITextFileProcessor {
		List<String> strList;
		public TextFileProcessor() {
			this.strList = new ArrayList<String>();
		}
		@Override public boolean processLine(String lineStr) throws InterpssException {
			this.strList.add(lineStr);
			return true;
		}
	}
}