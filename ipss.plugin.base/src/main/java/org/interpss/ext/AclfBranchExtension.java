package org.interpss.ext;

import java.util.HashMap;

/**
 *  AclfBranch extension 
 * 
 * @author mzhou
 *
 */
public class AclfBranchExtension extends HashMap<String,String> {
	private static final long serialVersionUID = 1L;
	/**
	 * to string function
	 */
	@Override
	public String toString() {
		String str = "AclfBranchExtension: " + super.toString();
		return str;
	}
}
