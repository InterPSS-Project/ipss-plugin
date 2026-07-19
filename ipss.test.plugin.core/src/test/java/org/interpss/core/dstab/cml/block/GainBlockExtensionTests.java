package org.interpss.core.dstab.cml.block;

import org.interpss.numeric.datatype.LimitType;

import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
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

