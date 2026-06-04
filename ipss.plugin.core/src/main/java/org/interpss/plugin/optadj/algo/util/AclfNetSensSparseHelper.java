package org.interpss.plugin.optadj.algo.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.complex.Complex;
import org.ejml.data.DMatrixSparseCSC;
import org.ejml.data.DMatrixSparseTriplet;
import org.ejml.ops.DConvertMatrixStruct;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.algo.dclf.solver.IDclfSolver;

/** 
* Helper class for calculating AclfNetwork sensitivities
* 
* @author  Donghao.F 
* @date 2023 Dec 29 11:47:22 
*/
public class AclfNetSensSparseHelper {
	private static Logger log = LoggerFactory.getLogger(AclfNetSensSparseHelper.class);

	private static final double ANGDIFF_THRESHOLD = 1e-6;
	private static final double BUS_SEN_THRESHOLD = 1e-3;
	private static final double TRANSFER_IMPEDANCE_THRESHOLD = 1e-6;
	
	// a AclfNetwork object
	private AclfNetwork aclfNet;
	
	/**
	 * Constructor
	 * 
	 * @param aclfNet
	 */
	public AclfNetSensSparseHelper(AclfNetwork aclfNet) {
		this.aclfNet = aclfNet;
	}
	
	/**
	 * calculate AclfNetwork sensitivities Sen[active bus][active branch]
	 * 
	 * @return
	 */
	public DMatrixSparseCSC calSen(){
		return calSenSortNumber();
	}
	
	/**
	 * calculate AclfNetwork sensitivities Sen[active bus][active branch]
	 * 
	 * @return
	 */
	public DMatrixSparseCSC calSenSortNumber(){
		return calSenSortNumber(aclfNet.getBusList().stream().map(bus->bus.getId()).collect(Collectors.toSet()));
	}
	

	public DMatrixSparseCSC calSenSortNumber(Set<String> busSet) {
		return calSenSortNumber(busSet, aclfNet.getBranchList().stream().map(bra->bra.getId()).collect(Collectors.toSet()));
	}
	
	public DMatrixSparseCSC calSenSortNumber(Set<String> busSet, Set<String> branchSet) {
		setNetBusBranchSortNumber(aclfNet);
		List<AclfBus> busList = aclfNet.getBusList().stream().filter(bus -> busSet.contains(bus.getId()))
				.collect(Collectors.toList());
		List<AclfBranch> branchList = aclfNet.getBranchList().stream().filter(bus -> branchSet.contains(bus.getId()))
				.collect(Collectors.toList());
		int busSize = busList.size();
		int branchSize = branchList.size();

		// Collect all non-zero elements first
		List<Triplet> triplets = new ArrayList<>();

		SenAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(aclfNet);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		IDclfSolver solver = dclfAlgo.getDclfSolver();
		
		for (int i = 0; i < busSize; i++) {
			AclfBus bus = busList.get(i);
			if (bus.isActive()) {
				try {
					double[] dblAry = solver.getSenPAngle(bus.getId());

					for (int j = 0; j < branchSize; j++) {
						AclfBranch branch = branchList.get(j);
						if (branch.isActive() && branch.getToAclfBus() != null) {
							BaseAclfBus<?, ?> fromBus = branch.getFromAclfBus();
							BaseAclfBus<?, ?> toBus = branch.getToAclfBus();
							double fAng = fromBus.isRefBus() ? 0.0 : dblAry[fromBus.getSortNumber()];
							double tAng = toBus.isRefBus() ? 0.0 : dblAry[toBus.getSortNumber()];
							double dAng = fAng - tAng;

							if (Math.abs(dAng) > ANGDIFF_THRESHOLD) {
								double b1ft = branch.b1ft();
								double value = -b1ft * dAng;
								if (Math.abs(value) > BUS_SEN_THRESHOLD) {
									triplets.add(new Triplet(bus.getSortNumber(), branch.getSortNumber(), value));
									
								}
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		// Use DMatrixSparseTriplet to collect entries in COO format
		// initLength should match the actual non-zero count; over-allocation causes frequent resizing and memory churn
		DMatrixSparseTriplet tripletMatrix = new DMatrixSparseTriplet(aclfNet.getNoBus(),aclfNet.getNoBranch(), triplets.size());
		for (Triplet t : triplets) {
			tripletMatrix.addItem(t.row, t.col, t.value);
		}

		// Convert to CSC format for subsequent numerical operations
		DMatrixSparseCSC cscMatrix = DConvertMatrixStruct.convert(tripletMatrix, (DMatrixSparseCSC) null);
//		triplets.forEach(t->{
//			System.out.println(t+","+ cscMatrix.get(t.row, t.col));
//		});
		return cscMatrix;
	}
	
	// Triplet holder
	static class Triplet {
	    int row, col;
	    double value;
	    Triplet(int row, int col, double value) {
	        this.row = row;
	        this.col = col;
	        this.value = value;
	    }
	    @Override
	    public String toString() {
	        return String.format("Triplet[row=%d, col=%d, value=%.6f]", row, col, value);
	    }
	}

	private void setNetBusBranchSortNumber(AclfNetwork aclfNet) {
		aclfNet.arrangeBusNumber();
		int i = 0;
		for (AclfBranch branch : aclfNet.getBranchList()) {
			branch.setSortNumber(i++);
		}
	}
	
	/**
	 * Calculate sparse transfer impedance between buses (indexed by sortNumber)
	 * 
	 * @param busSet source bus ID set (injection buses)
	 * @param targetBusSet target bus ID set (buses whose voltage is observed)
	 * @return CSC sparse matrix with row = source bus sortNumber and column = target bus sortNumber
	 */
	public DMatrixSparseCSC calculateTransferImpedanceSparse(Set<String> busSet, Set<String> targetBusSet) {
		// 1. Get source and target bus lists
	    List<AclfBus> sourceBuses = aclfNet.getBusList().stream()
	            .filter(bus -> busSet.contains(bus.getId()))
	            .collect(Collectors.toList());
	    
	    List<AclfBus> targetBuses = aclfNet.getBusList().stream()
	            .filter(bus -> targetBusSet.contains(bus.getId()))
	            .collect(Collectors.toList());
	    
	    // 2. Store non-zero triplets (row, col, value)
	    List<Triplet> triplets = new ArrayList<>();
	 // For each source bus, inject unit current and solve one network equation
        ISparseEqnComplex eqn = aclfNet.formYMatrix();
	    // 3. Compute transfer impedance
	    for (AclfBus sourceBus : sourceBuses) {
	        int sourceSortNum = sourceBus.getSortNumber();
	        
	        // Inject unit current at the source bus using sortNumber as the index
	        
	        // Use voltage difference at the target bus as transfer impedance
	        for (AclfBus targetBus : targetBuses) {
	        	int targetSortNum = targetBus.getSortNumber();
	        	eqn.setB2Zero(); // reset RHS vector
	            
	            eqn.setBi(new Complex(1.0, 0.0), sourceSortNum);
		        eqn.setBi(new Complex(-1.0, 0.0), targetSortNum);
		        try {
		            eqn.solveEqn(); // solve bus voltages
		        } catch (IpssNumericException e) {
		            e.printStackTrace();
		            continue;
		        }
	            
		        Complex c = eqn.getX(sourceSortNum).subtract(eqn.getX(targetSortNum));
	            double value = c.getReal();// take real part
	            
	            // store only non-zero values
	            if (Math.abs(value) > TRANSFER_IMPEDANCE_THRESHOLD) {
	                triplets.add(new Triplet(sourceSortNum, targetSortNum, value));
	            }
	        }
	    }
	    
	    // 4. Build sparse matrix with dimensions = total bus count
	    int totalBusCount = aclfNet.getNoBus();
	    DMatrixSparseTriplet tripletMatrix = new DMatrixSparseTriplet(
	        totalBusCount, totalBusCount, triplets.size()
	    );
	    
	    for (Triplet t : triplets) {
	        tripletMatrix.addItem(t.row, t.col, t.value);
	    }
	    
	    // Convert to CSC format for subsequent numerical operations
	    DMatrixSparseCSC cscMatrix = DConvertMatrixStruct.convert(tripletMatrix, (DMatrixSparseCSC) null);
	    
	    return cscMatrix;
	}

	
}
