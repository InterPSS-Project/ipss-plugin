/*
 * @(#)NetModelComparator.java   
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
 * @Date 06/12/2011
 * 
 *   Revision History
 *   ================
 *
 */

package  org.interpss.QA.compare;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.net.Network;

public class NetModelComparator<TBus extends Bus, TBranch extends Branch> {
	private Network<TBus, TBranch> baseNet = null;
	public NetModelComparator(Network<TBus, TBranch> baseNet) {
		this.baseNet = baseNet;
	}
	
	/**
	 * compare aclfNet with the baseNet using the AclfBusComparator
	 * 
	 * @param aclfNet
	 * @param busComp
	 * @return
	 */
	public boolean compare(BaseAclfNetwork<?,?> aclfNet, IAclfBusComparator busComp) {
		return compare(aclfNet, null, busComp, null, false);
	}
	
	/**
	 * compare aclfNet with the baseNet using the AclfBusComparator and the AclfBranchComparator 
	 * 
	 * @param aclfNet
	 * @param busComp
	 * @param braComp
	 * @return
	 */
	public boolean compare(AclfNetwork aclfNet, IAclfBusComparator busComp, IAclfBranchComparator braComp) {
		return compare(aclfNet, null, busComp, braComp, false);
	}
	
	/**
	 * compare aclfNet with the baseNet using the AclfBusComparator, the AclfBranchComparator
	 * and the AclfNetComparator 
	 * 
	 * @param aclfNet
	 * @param netComp
	 * @param busComp
	 * @param braComp
	 * @return
	 */
	public boolean compare(BaseAclfNetwork<?,?> aclfNet, IAclfNetComparator netComp, IAclfBusComparator busComp, IAclfBranchComparator braComp) {
		return compare(aclfNet, netComp, busComp, braComp, false);
	}
	public boolean compare(BaseAclfNetwork<?,?> aclfNet, IAclfNetComparator netComp, IAclfBusComparator busComp, IAclfBranchComparator braComp, boolean debug) {
		boolean ok = true;
		
		if (netComp != null)
			if (!netComp.compare((AclfNetwork)baseNet, aclfNet)) {
				if (debug) {
					
				}
				ok = false;
			}
			
		if (busComp != null)
			for (Bus b : baseNet.getBusList()) {
				if (!busComp.compare((BaseAclfBus)b, aclfNet.getBus(b.getId()))) {
					if (debug) {
						System.out.println("\n////////////////// Begin ///////////////////////////");
						
						System.out.println("-------------Base Bus Object------------");
						System.out.println(((AclfBus)b).toString(baseNet.getBaseKva()));

						if (aclfNet.getBus(b.getId()) != null) {
							System.out.println("-------------Compare Bus Object------------");
							System.out.println(aclfNet.getBus(b.getId()).toString(baseNet.getBaseKva()));
						}
						
						System.out.println("//////////////////  End  ///////////////////////////\n");
					}
					ok = false;
				}
			}

		if (braComp != null)
			for (Branch b : baseNet.getBranchList()) {
				if (!braComp.compare((AclfBranch)b, aclfNet.getBranch(b.getId()))) {
					if (debug) {
						System.out.println("\n////////////////// Begin ///////////////////////////");
						
						System.out.println("-------------Base Branch Object------------");
						System.out.println(((AclfBranch)b).toString(baseNet.getBaseKva()));

						if (aclfNet.getBranch(b.getId()) != null) {
							System.out.println("-------------Compare Branch Object------------");
							System.out.println(aclfNet.getBranch(b.getId()).toString(baseNet.getBaseKva()));
						}
						else {
							System.out.println("-------------Compare Branch Object------------");
							System.out.println("Compare branch missing");
						}
						
						System.out.println("//////////////////  End  ///////////////////////////\n");
					}
					ok = false;
				}
			}
		
		if (ok) 
			System.out.println("Model Comparison has no Error");
		else
			System.out.println("********** Model Comparison has Error");
		return ok;
	}
}
