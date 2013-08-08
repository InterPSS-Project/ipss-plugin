package org.interpss.ext;

import java.util.Hashtable;

/**
 *  AclfBranch extension 
 * 
 * @author mzhou
 *
 */
public class AclfBranchExtension extends Hashtable<String,String> {
	private static final long serialVersionUID = 1L;
	/**
	 * to string function
	 */
	public String toString() {
		String str = "AclfBranchExtension: " + super.toString();
		return str;
	}
}
