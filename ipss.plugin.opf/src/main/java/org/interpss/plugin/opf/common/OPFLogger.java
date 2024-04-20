package org.interpss.plugin.opf.common;

import java.util.logging.Logger;

public class OPFLogger {
	
	private static Logger logger = null;

	public static void setLogger(Logger l) {
		logger = l;
	}

	public static Logger getLogger() { 
		if (logger == null) 
			logger = Logger.getLogger("ipss.plugin.opf");
		return logger; 
	}

}
