 /*
  * @(#)DelayControlBlockTests.java   
  *
  * Copyright (C) 2006 www.interpss.org
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
  * @Date 09/15/2006
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.dstab.control.cml.block;

import org.interpss.numeric.datatype.LimitType;

import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.controller.block.ICMLStaticBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;

public class GainBlockExtensionTests {
	public void some() {
	}
}

@AnController(
        input="this.refPoint + pss.vs - mach.vt",
        output="this.delayBlock.y",
        refPoint="this.delayBlock.u0 - pss.vs + mach.vt",
        display= {}
)

class CustomGainBlock {

	public double kg = 1.0/*constant*/, kc = 0.14, vrmax = 5.30, vrmin = -5.11;
	   @AnControllerField(
	      type=CMLFieldEnum.StaticBlock,
	      input="this.kaDelayBlock.y",
	      y0="mach.efd"  )
	   // extend the GainBlock to reuse its functionality   
	   public ICMLStaticBlock gainCustomBlock = new GainBlock() {
		  @Override
		  public boolean initStateY0(double y0) {
			  // at the initial point, set the gain block gain
			  super.k = kg;
			  return super.initStateY0(y0);
		  }

		  @Override
		  public double getY() {
			  // before returning Y, calculate the limit
			  double max = calLimit(vrmax), min = calLimit(vrmin);
			  // reset the gain block limit
			  super.limit = new LimitType(max, min);
			  // ask the gain block to calculate the output
				return super.getY();
		  }
		  
		  private double calLimit(double vrlimit) {
//		      Machine mach = getMachine();
//		      DStabBus dbus = mach.getDStabBus();
//		      double vt = mach.getVdq(dbus).abs();
//		      double ifd = mach.calculateIfd(dbus);
//		      return vt * vrlimit - kc * ifd; 
			  return 0.0; // TODO create your limit calculation routine 
		  }
	   };
} 

