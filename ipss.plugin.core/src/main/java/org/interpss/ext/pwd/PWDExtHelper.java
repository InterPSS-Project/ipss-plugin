package org.interpss.ext.pwd;

import com.interpss.core.net.Bus;

/**
 * Helper functions for PowerWorld extension
 * 
 * @author mzhou
 *
 */
public class PWDExtHelper {
	/**
	 * get bus substation name
	 * 
	 * @param bus
	 * @return
	 */
	public static String getSubstationName(Bus bus) {
		AclfBusPWDExtension ext = (AclfBusPWDExtension)(bus.getExtensionObject());
		return ext.get("SubStation");
	}
}
