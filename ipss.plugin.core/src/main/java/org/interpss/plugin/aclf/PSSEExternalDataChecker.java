package org.interpss.plugin.aclf;

import java.util.List;
import java.util.function.Consumer;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.adj.PSXfrPControl;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.funcImpl.ParallelBranchUtilFunc;
import com.interpss.core.net.Branch;

/**
 * This class is used to perform external data checking for PSSE created AclfNetwork.
 *   
 */
public class PSSEExternalDataChecker implements Consumer<BaseAclfNetwork<?,?>>{
	public static final double HighBusXRRatio = 500.0; // threshold for high X/R ratio checking
	public static final double LowBranchReactance = 0.0005; // threshold for low reactance checking
	public static final double HighBranchReactance = 1.0; // threshold for high reactance checking
	public static final double LowBranchXRRatio = 0.67; // threshold for low X/R ratio checking
	public static final double LargeBranchChargeB = 5.0; // threshold for Large line-charging admittance
	public static final double HighTurnRatio = 1.1; // threshold for high tap ratio
	public static final double LowTurnRatio = 0.9; // threshold for low tap ratio
	
	/**
	 * Constructor
	 * 
	 * @param dataChecker a Consumer that initialize this data checker object
	 */
	public PSSEExternalDataChecker(Consumer<PSSEExternalDataChecker> dataChecker) {
		dataChecker.accept(this);
	}
	
	@Override
	public void accept(BaseAclfNetwork<?,?> aclfNet) {
		/*
		 Transformer Adjustment Data Checking Reports
			1.1 Voltage or Mvar No Tap-Step Specified STEP=0.
			1.2 Voltage or Mvar Small Tap-Step 0.3 Voltage or Mvar Large Tap-Step STEP>0.00625
			1.4 Voltage Band Small Compared to Tap-Step Size BAND<2*STEP
			1.5 Voltage Small Voltage Band BAND<0.02
			1.6 Voltage Large Voltage Band BAND>0.02
			1.8 MW or Mvar Small Flow Band Band<5.0
			1.9 MW or Mvar Large Flow Band Band>5.0
		 
		 Exceptional Branch Data Reports
			2.1 Low-reactance branches x<0.0005
			2.2 High-reactance branches x>1.0
			2.3 Branches with low X/R ratio R>0.67X
			2.4 Negative-reactance branches X<0
			2.5 Buses with high ratio of highest to lowest reactance of connected branches Ratio>500
			2.6 Large line-charging admittance Bch>5.0
			2.7 Nonidentical parallel transformers 
			2.8 Transformers with high tap ratio t>1.1
			2.9 Transformers with low tap ratio t<0.9
		 */
		
		//double baseMva = aclfNet.getBaseMva(); // convert to MVA
		
		aclfNet.getBusList().forEach(bus -> {
			// 2.5 Buses with high ratio of highest to lowest reactance of connected branches Ratio>500
			bus.getBranchList().stream()
			    .map(bra -> (AclfBranch)bra)
				.filter(branch -> branch.isLine())
				.mapToDouble(branch -> branch.getZ().getImaginary())
				.filter(x -> x > 0)
				.max()
				.ifPresent(maxX -> {
					bus.getBranchList().stream()
						.map(bra -> (AclfBranch)bra)
						.filter(branch -> branch.isLine())
						.mapToDouble(branch -> branch.getZ().getImaginary())
						.filter(x -> x > 0)
						.min()
						.ifPresent(minX -> {
							if (maxX / minX > HighBusXRRatio) {
								System.out.println("Bus " + bus.getId() + " has high reactance ratio: " + (maxX / minX));
							}
						});
				});
		});
		
		aclfNet.getBranchList().forEach(branch -> {
			if (branch.isLine()) {
				// 2.1 Low-reactance branches x<0.0005
				if (branch.getZ().getImaginary() < LowBranchReactance) {
					System.out.println("Branch " + branch.getId() + " has low reactance: " + branch.getZ().getImaginary());
				}
				
				// 2.2 High-reactance branches x>1.0
				if (branch.getZ().getImaginary() > HighBranchReactance) {
					System.out.println("Branch " + branch.getId() + " has high reactance: " + branch.getZ().getImaginary());
				}
				
				// 2.3 Branches with low X/R ratio R>0.67X
				if (branch.getZ().getReal() > LowBranchXRRatio * branch.getZ().getImaginary()) {
					System.out.println("Branch " + branch.getId() + " has low X/R ratio: " + 
							branch.getZ().getReal() / branch.getZ().getImaginary());
				}
				
				// 2.4 Negative-reactance branches X<0
				if (branch.getZ().getImaginary() < 0) {
					System.out.println("Branch " + branch.getId() + " has negative reactance: " + branch.getZ().getImaginary());
				}
				
				// 2.6 Large line-charging admittance Bch>5.0
				if (2.0*branch.getHShuntY().getImaginary() > LargeBranchChargeB) {
					System.out.println("Branch " + branch.getId() + " has large line-charging admittance: " + 
							2.0*branch.getHShuntY().getImaginary());
				}
			}
			else if (branch.isXfr() || branch.isPSXfr()) {
				// 2.7 Nonidentical parallel transformers 
				List<Branch> paraBranchList = ParallelBranchUtilFunc.getParallelBranchList(branch);
				if (paraBranchList.size() > 1) {
					boolean isIdentical = paraBranchList.stream()
						.map(bra -> (AclfBranch)bra)
						.allMatch(aclfBranch -> identicalXfr(branch, aclfBranch));
					if (!isIdentical) {
						System.out.println("Transformer " + branch.getId() + " has non-identical parallel transformers.");
					}
				}
				
				// 2.8 Transformers with high tap ratio t>1.1
				if (branch.getFromTurnRatio() > HighTurnRatio || branch.getToTurnRatio() > HighTurnRatio) {
					System.out.println("Transformer " + branch.getId() + " has high tap ratio: " + 
							branch.getFromTurnRatio() + ", " + branch.getToTurnRatio());
				}
				
				// 2.9 Transformers with low tap ratio t<0.9
				if (branch.getFromTurnRatio() < LowTurnRatio || branch.getToTurnRatio() < LowTurnRatio) {
					System.out.println("Transformer " + branch.getId() + " has low tap ratio: " + 
							branch.getFromTurnRatio() + ", " + branch.getToTurnRatio());
				}
				
				if (branch.isTapControl()) {
					TapControl tapControl = branch.getTapControl();
					// TODO
					// 1.1 Voltage or Mvar No Tap-Step Specified STEP=0.
					// 1.2 Voltage or Mvar Small Tap-Step 0.3 Voltage or Mvar Large Tap-Step STEP>0.00625
					// 1.4 Voltage Band Small Compared to Tap-Step Size BAND<2*STEP
					// 1.5 Voltage Small Voltage Band BAND<0.02
					// 1.6 Voltage Large Voltage Band BAND>0.02
					// 1.8 MW or Mvar Small Flow Band Band<5.0
					// 1.9 MW or Mvar Large Flow Band Band>5.0
				}
				else if (branch.isPSXfrPControl()) {
					PSXfrPControl psXfrControl = branch.getPSXfrPControl();
					
					// TODO
					// 1.8 MW or Mvar Small Flow Band Band<5.0 	
					// 1.9 MW or Mvar Large Flow Band Band>5.0 
				}
			}
		});
  	}
	
	private boolean identicalXfr(AclfBranch branch, AclfBranch paraBranch) {
		boolean rtn = branch.getFromTurnRatio() == paraBranch.getFromTurnRatio() &&
			   branch.getToTurnRatio() == paraBranch.getToTurnRatio() &&
			   branch.getZ().getReal() == paraBranch.getZ().getReal() &&
			   branch.getZ().getImaginary() == paraBranch.getZ().getImaginary();
		if (branch.isPSXfr()) {
			rtn = rtn && branch.getFromPSXfrAngle() == paraBranch.getFromPSXfrAngle() &&
					branch.getToPSXfrAngle() == paraBranch.getToPSXfrAngle();
		}
		return rtn;
	}
}
