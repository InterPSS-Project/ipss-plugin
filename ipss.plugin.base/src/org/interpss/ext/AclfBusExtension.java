package org.interpss.ext;

import java.util.Hashtable;

/**
 *  AclfBus extension 
 *   
 * @author mzhou
 *
 */
public class AclfBusExtension extends Hashtable<String,String> {
	private static final long serialVersionUID = 1L;
	
	/**
	 * to string function
	 */
	public String toString() {
		String str = "AclfBusExtension: " + super.toString();
		return str;
	}
}
