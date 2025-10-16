package org.interpss.plugin.aclf.config;

import java.io.IOException;

import org.interpss.datatype.base.BaseJSONBean;
import org.interpss.util.FileUtil;

import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.AdjustApplyType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.NrMethodConfig;
import com.interpss.core.algo.NrOptimizeAlgoType;

/**
 * Loadflow run configuration record
 * 
 */
public class AclfRunConfigRec extends BaseJSONBean {
	public AclfMethodType lfMethod = AclfMethodType.NR;
	public double tolerance = 0.0001;
	public int maxIterations = 20;
	public boolean autoSetZeroZBranch = true;
	public boolean turnOffIslandBus = true;
	public boolean autoTurnLine2Xfr = true;
	
	public boolean includeAdjustments = false;
	public boolean activateAllAdjCtrl = false;
	
	public boolean applyLimitControl = true;
	public boolean pvBusLimitControl = true;
	public boolean pqBusLimitControl = true;
	public boolean limitBackoffCheck = false;
	public boolean checkGenQLimImmediate = false;
	
	public boolean applyVoltAdjust = true;
	public boolean applyDiscreteAdjust = true;
	public boolean remoteQBusControl = true;
	public boolean switchedShuntAdjust = true;
	public boolean svcFactsAdjust = true;
	public boolean xfrTapControl = true;
	
	public boolean applyPowerAdjust = true;
	public boolean psXfrPControl = true;
	
	// NR method config
	public boolean nonDivergent = false;
	public NrOptimizeAlgoType optAlgo = NrOptimizeAlgoType.CUBIC_EQN_STEP_SIZE;
	public boolean variableUpdateLimit = false;
	public double deltaVAngLimit = 0.2;
	public double deltaVMagLimit = 0.1;
	public boolean stopNoSolutionFound = false;
	public double minScaleFactor = 0.01;
	
	// Adjustment/Control settings
	public int limitCtrlStartPoint = 10;
	public double limitCtrlTolearnceFactor = 10.0;
	public AdjustApplyType limitCtrlApplyType = AdjustApplyType.DURING_ITERATION;
	
	public int voltAdjStartPoint = 10;
	public double voltAdjTolearnceFactor = 10.0;
	public AdjustApplyType voltAdjApplyType = AdjustApplyType.DURING_ITERATION;
	
	public int powerAdjStartPoint = 10;
	public double powerAdjTolearnceFactor = 10.0;
	public AdjustApplyType powerAdjApplyType = AdjustApplyType.POST_ITERATION;
	
	public double pvLimitAccFactor = 1.0;
	public double pqLimitAccFactor = 1.0;
	public double reQBusAccFactor = 1.0;
	public double svcAccFactor = 1.0;
	public double xfrTapAccFactor = 1.0;
	public double psXfrPContrlAccFactor = 1.0;
	
	public static AclfRunConfigRec loadAclfRunConfig(String configFilename) throws IOException {
    	String json = FileUtil.readFileAsString(configFilename);
    	//System.out.println("Loaded saved AC Loadflow config: \n" + json);
    	return new AclfRunConfigRec().fromString(json);
	}
	
	public void saveAclfRunConfig(String configFilename) {
		String json = this.toString();
		FileUtil.writeText2File(configFilename, json);
	}
	
	public void configAclfRun(LoadflowAlgorithm algo, boolean appluAdjust, boolean psseConfig) {
        algo.setLfMethod(this.lfMethod);

        // Set tolerance and max iterations
        algo.setTolerance(this.tolerance);
        algo.setMaxIterations(this.maxIterations);
        
        algo.getDataCheckConfig().setAutoSetZeroZBranch(this.autoSetZeroZBranch);
        algo.getDataCheckConfig().setTurnOffIslandBus(this.turnOffIslandBus);
    	algo.getDataCheckConfig().setAutoTurnLine2Xfr(this.autoTurnLine2Xfr);
        
        // include adjustments/controls
        if (!appluAdjust) {
    		// not need to turn off the power adjustment, since the turn of Adjust will turn off the power adjustment
            //algo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
            algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
        } 
        else {
        	algo.getLfAdjAlgo().setApplyAdjustAlgo(true);
        	algo.getLfAdjAlgo().setActivateAllAdjust(this.activateAllAdjCtrl);
        	
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setAdjust(this.applyLimitControl);
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setPvLimitControl(this.pvBusLimitControl);
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setPqLimitControl(this.pqBusLimitControl);
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setLimitBackoffCheck(this.limitBackoffCheck);
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setCheckGenQLimitImmediate(this.checkGenQLimImmediate);
        	
        	algo.getLfAdjAlgo().getVoltAdjConfig().setAdjust(this.applyVoltAdjust);
        	algo.getLfAdjAlgo().getVoltAdjConfig().setDiscreteAdjust(this.applyDiscreteAdjust);
			algo.getLfAdjAlgo().getVoltAdjConfig().setReQBusControl(this.remoteQBusControl);	
			algo.getLfAdjAlgo().getVoltAdjConfig().setSwitchedShuntAdjust(this.switchedShuntAdjust);
			algo.getLfAdjAlgo().getVoltAdjConfig().setSvcFactsAdjust(this.svcFactsAdjust);
			algo.getLfAdjAlgo().getVoltAdjConfig().setXfrTapControl(this.xfrTapControl);

        	algo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(this.applyPowerAdjust);
        	algo.getLfAdjAlgo().getPowerAdjConfig().setPsXfrPControl(this.psXfrPControl);
        	
	        // NR Config tab inputs
	        if (this.lfMethod == AclfMethodType.NR) {
	        	NrMethodConfig config = algo.getNrMethodConfig();
	    	  	config.setNonDivergent(this.nonDivergent);
	    	  	if (this.nonDivergent) {
	        	  	config.setOptAlgo(this.optAlgo); 
	        	  	config.setVariableUpdateLimit(this.variableUpdateLimit);
	        	  	config.setDeltaVAngLimit(this.deltaVAngLimit);
	        	  	config.setDeltaVMagLimit(this.deltaVMagLimit);
	        	  	config.setStopNoSolutionFound(this.stopNoSolutionFound);
	        	  	config.setMinScaleFactor(this.minScaleFactor);
	        	  	// re-configure the Nr solver with the updated config
	        	  	algo.getLfCalculator().getNrSolver().reConfigSolver(config);
	    	  	}
	        }

        	// Adj/Ctrl Setting tab inputs to be processed
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setStartPoint(this.limitCtrlStartPoint);
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setToleranceFactor(this.limitCtrlTolearnceFactor);
        	algo.getLfAdjAlgo().getLimitCtrlConfig().setAdjustAppType(this.limitCtrlApplyType);

        	algo.getLfAdjAlgo().getVoltAdjConfig().setStartPoint(this.voltAdjStartPoint);
        	algo.getLfAdjAlgo().getVoltAdjConfig().setToleranceFactor(this.voltAdjTolearnceFactor);
        	algo.getLfAdjAlgo().getVoltAdjConfig().setAdjustAppType(this.voltAdjApplyType);

        	algo.getLfAdjAlgo().getPowerAdjConfig().setStartPoint(this.powerAdjStartPoint);
        	algo.getLfAdjAlgo().getPowerAdjConfig().setToleranceFactor(this.powerAdjTolearnceFactor);
        	algo.getLfAdjAlgo().getPowerAdjConfig().setAdjustAppType(this.powerAdjApplyType);
        	
        	algo.getLfAdjAlgo().getAccFactorConfig().setPvLimitAccFactor(this.pvLimitAccFactor);
			algo.getLfAdjAlgo().getAccFactorConfig().setPqLimitAccFactor(this.pqLimitAccFactor);
			algo.getLfAdjAlgo().getAccFactorConfig().setReQBusAccFactor(this.reQBusAccFactor);
			algo.getLfAdjAlgo().getAccFactorConfig().setSvcAdjustAccFactor(this.svcAccFactor);
			algo.getLfAdjAlgo().getAccFactorConfig().setXfrTapControlAccFactor(this.xfrTapAccFactor);
			algo.getLfAdjAlgo().getAccFactorConfig().setPsXfrPControlAccFactor(this.psXfrPContrlAccFactor);
        
            // PSS/E setting tab inputs to be processed
            if (psseConfig) {
				// PSS/E setting tab inputs to be processed
            	algo.getLfAdjAlgo().initialize(lfAdjAlgo -> {
            	  	// TODO do PSS/E specific configuration before running the Loadflow
            	});
			}
        }
	}
}
