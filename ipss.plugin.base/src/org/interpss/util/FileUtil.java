/*
 * @(#)FileUtil.java   
 *
 * Copyright (C) 2006-2008 www.interpss.com
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
 * @Date 01/30/2008
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.util;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.interpss.common.util.IpssLogger.ipssLogger;

/**
 * File utility
 * 
 * @author mzhou
 *
 */
public class FileUtil {
	/**
	 * Write the text to the file
	 * 
	 * @param filename filename
	 * @param text
	 * @return
	 */
	public static boolean writeText2File(String filename, String text) {
		return write2File(filename, text.getBytes());
	}
	
	/**
	 * Write the text to the file
	 * 
	 * @param filename filename
	 * @param bytes
	 * @return
	 */
	public static boolean write2File(String filename, byte[] bytes) {
		ipssLogger.info("FileUtil.writeTextarea2File() info to file: " + filename);
		try {
			OutputStream out = new BufferedOutputStream(new FileOutputStream(filename));
			out.write(bytes);
			out.flush();
			out.close();
			return true;
		} catch (Exception e) {
			ipssLogger.severe("Cannot save to file: " + filename + ", " + e.toString());
		}
		return false;
	}
	
	/**
	 * read a file and return file context as a byte[]
	 * 
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static byte[] readFile(File file) throws IOException {
		InputStream inStream = new FileInputStream(file);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		int n;
		byte[] buf = new byte[4096];
		do {
			n = inStream.read(buf);
			if (n >= 0)
				bos.write(buf, 0, n);
		} while (n > 0);
		inStream.close();
		return bos.toByteArray();
	}	
}
