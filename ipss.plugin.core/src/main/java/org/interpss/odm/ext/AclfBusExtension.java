package org.interpss.odm.ext;

import java.util.HashMap;

/**
 *  AclfBus extension 
 *   
 * @author mzhou
 *
 */
public class AclfBusExtension extends HashMap<String,String> {
	private static final long serialVersionUID = 1L;
	
	/**
	 * to string function
	 */
	@Override
	public String toString() {
		String str = "AclfBusExtension: " + super.toString();
		return str;
	}
}
