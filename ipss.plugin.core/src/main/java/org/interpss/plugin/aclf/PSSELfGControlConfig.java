package org.interpss.plugin.aclf;

import java.util.function.Consumer;

import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.adj.VarCompensationMode;
import com.interpss.core.algo.LfAdjustAlgorithm;

/**
 * This class is used to configure the PSSE global adjustment/control for the load flow
 * adjustment algorithm in InterPSS.
 *   
 */
public class PSSELfGControlConfig implements Consumer<LfAdjustAlgorithm>{
	public static final int SwitchedShunt_LockAll = 1;
	public static final int SwitchedShunt_Continuous = 2;
	public static final int SwitchedShunt_Discrete = 3;
	
	public int gCtrlSwitchedShunt = SwitchedShunt_Discrete;
	
	/**
	 * Constructor
	 * 
	 * @param initializer a Consumer that initialize this configuration object
	 */
	public PSSELfGControlConfig(Consumer<PSSELfGControlConfig> initializer) {
		initializer.accept(this);
	}
	
	@Override
	public void accept(LfAdjustAlgorithm lfAdjAlgo) {
  		lfAdjAlgo.getLfAlgo().getAclfNet().getBusList().forEach(bus -> {
  			if (bus.isSwitchedShunt()) {
  				SwitchedShunt swShunt = bus.getSwitchedShunt();
  				swShunt.setControlMode(
  						this.gCtrlSwitchedShunt == SwitchedShunt_LockAll? VarCompensationMode.FIXED : 
  							this.gCtrlSwitchedShunt == SwitchedShunt_Continuous? VarCompensationMode.CONTINUOUS : 
  								VarCompensationMode.DISCRETE);
  			}
  		});	
  	}

}
