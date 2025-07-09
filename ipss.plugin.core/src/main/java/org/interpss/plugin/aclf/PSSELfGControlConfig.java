package org.interpss.plugin.aclf;

import java.util.function.Consumer;

import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
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

	public static final int TapControl_LockTaps = 4;
	public static final int TapControl_Direct = 5;
	public static final int TapControl_Stepping = 6;
	
	public static final int VarLimit_Ignore = 7;
	public static final int VarLimit_Immediate = 8;
	public static final int VarLimit_Automatic = 9;
	public static final int VarLimit_CertainItr = 10;
	
	public int gCtrlSwitchedShunt = SwitchedShunt_Discrete;
	public int gCtrlTapControl = TapControl_Stepping;
	public int gCtrlVarLimit = VarLimit_Automatic;
	
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
  			if (bus.isSwitchedShunt()) {   // Switched shunt control and Static var compensator control
  				SwitchedShunt swShunt = bus.getSwitchedShunt();
  				swShunt.setControlMode(
  						this.gCtrlSwitchedShunt == SwitchedShunt_LockAll? AclfAdjustControlMode.FIXED : 
  							this.gCtrlSwitchedShunt == SwitchedShunt_Continuous? AclfAdjustControlMode.CONTINUOUS : 
  								AclfAdjustControlMode.DISCRETE);
  			}
  			else if (bus.isPQBusLimit() || bus.isRemoteQBus()) {
  				if (this.gCtrlVarLimit == VarLimit_Ignore) {
  					bus.getBusControl().setControlStatus(false);
  				} 
  			}
  		});	
  		
  		lfAdjAlgo.getLfAlgo().getAclfNet().getBranchList().forEach(tapBranch -> {
  			if (tapBranch.isTapControl()) {
  				TapControl tapCtrl = tapBranch.getTapControl();
  				tapCtrl.setControlStatus(
  						this.gCtrlTapControl == TapControl_LockTaps? false : 
  							this.gCtrlTapControl == TapControl_Direct? true : 
  								true);
  			}
  		});
  	}

}
