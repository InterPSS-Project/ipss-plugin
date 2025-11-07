package org.interpss.plugin.aclf.config.psse;

import java.util.function.Consumer;

import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.algo.LfAdjustAlgorithm;

/**
 * This class is used to configure the PSSE adjustment/control for the load flow
 * adjustment algorithm in InterPSS.
 *   
 */
public class PSSELfAdjControlConfig implements Consumer<LfAdjustAlgorithm>{
	public static final int SwitchedShunt_LockAll = 1;
	public static final int SwitchedShunt_ContinuousOnly = 2;
	public static final int SwitchedShunt_EnableAll = 3;

	public static final int TapControl_LockAll = 4;
	public static final int TapControl_Direct = 5;
	public static final int TapControl_Stepping = 6;
	
	public static final int GenVarLimit_Ignore = 7;
	public static final int GenVarLimit_Immediate = 8;
	public static final int GenVarLimit_Automatic = 9;
	public static final int GenVarLimit_StartItr = 10;
	//TODO: hvdc tap control; phase shift control, inter-area tieline exchange control
	public static final int HVDCTapControl_LockAll = 11;
	public static final int HVDCTapControl_Immediate = 12;
	public static final int HVDCTapControl_Automatic = 13;

	public static final int PhaseShiftControl_Disabled = 14;
	public static final int PhaseShiftControl_Enabled = 15;

	public static final int AreaInterChangeControl_Disabled = 16;
	public static final int AreaInterChangeControl_TieLineOnly = 17;
	public static final int AreaInterChangeControl_EnableAll = 18;

	public int gCtrlSwitchedShunt = SwitchedShunt_LockAll;
	public int gCtrlTapControl = TapControl_Stepping;
	public int gCtrlVarLimit = GenVarLimit_Automatic;

	//TODO: hvdc tap control; phase shift control, inter-area tieline exchange control
	public int gCtrlHVDCTapControl = HVDCTapControl_Automatic;
	public int gCtrlPhaseShiftControl = PhaseShiftControl_Disabled;
	public int gCtrlAreaInterChangeControl = AreaInterChangeControl_Disabled;


	/**
	 * Constructor
	 * 
	 * @param initializer a Consumer that initialize this configuration object
	 */
	public PSSELfAdjControlConfig(Consumer<PSSELfAdjControlConfig> initializer) {
		initializer.accept(this);
	}
	
	@Override
	public void accept(LfAdjustAlgorithm lfAdjAlgo) {
  		lfAdjAlgo.getLfAlgo().getAclfNet().getBusList().forEach(bus -> {
			// Switched Shunt control configuration
			if (!bus.getSwitchedShuntList().isEmpty()) {
				switch(this.gCtrlSwitchedShunt){
					case SwitchedShunt_LockAll:
						bus.getSwitchedShuntList().forEach(swShunt -> {
							if (swShunt.isControlStatus()) swShunt.setControlMode(AclfAdjustControlMode.FIXED);
						});
						break;
					case SwitchedShunt_ContinuousOnly:
						bus.getSwitchedShuntList().forEach(swShunt -> {
							if (swShunt.isControlStatus() && swShunt.getControlMode() != AclfAdjustControlMode.CONTINUOUS)
								swShunt.setControlMode(AclfAdjustControlMode.FIXED);
						});
						break;
					case SwitchedShunt_EnableAll:
						bus.getSwitchedShuntList().forEach(swShunt -> {
							if (swShunt.isControlStatus()) swShunt.setControlMode(AclfAdjustControlMode.DISCRETE);
						});
						break;
					// default: // Discrete, no action is needed
						
				}
			}


		
  			// Var Limit control configuration applied to Generator PV bus limt and remote Q bus
  			else if (bus.isGen() && (bus.isPVBusLimit() || bus.isRemoteQBus())) {
  				if (this.gCtrlVarLimit == GenVarLimit_Ignore) {
  					//bus.getBusControl().setControlStatus(false);
					//TODO: this means the Mvar limits are ignored for all the generators in the case during a power flow solution. 

  				} 
  				else if (this.gCtrlVarLimit == GenVarLimit_Immediate) {
  					// we use a large start point to implement this configuration
  					// the control will be applied when the max mismatch less than startPoint * tolerance
  					lfAdjAlgo.getVoltAdjConfig().setStartPoint(999999);
  				} 
  				else if (this.gCtrlVarLimit == GenVarLimit_Automatic) {
  					// this is the default setting in InterPSS, no need to set it
  				} 
  				else if (this.gCtrlVarLimit == GenVarLimit_StartItr) {
  					// not supported in InterPSS
  				} 
  			}
  		});	
  		
  		lfAdjAlgo.getLfAlgo().getAclfNet().getBranchList().forEach(tapBranch -> {
  			if (tapBranch.isTapControl()) {
  				TapControl tapCtrl = tapBranch.getTapControl();
  				tapCtrl.setControlStatus(
  						this.gCtrlTapControl == TapControl_LockAll? false : 
  							this.gCtrlTapControl == TapControl_Direct? true : 
  								true);
  			}
  		});

		// phase shift control configuration
	
		lfAdjAlgo.getLfAlgo().getAclfNet().getBranchList().forEach(phxfr -> {
			if (phxfr.isPSXfrPControl()) {
				if (this.gCtrlPhaseShiftControl == PhaseShiftControl_Disabled) {
					phxfr.getPSXfrPControl().setControlStatus(false);
				} else {
					phxfr.getPSXfrPControl().setControlStatus(true);
				}
			}
		});
		
	}
}
