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

		// ���ռ����з���Ԫ��
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

							if (Math.abs(dAng) > 1e-6) {
								double b1ft = branch.b1ft();
								double value = -b1ft * dAng;
								if (Math.abs(value) > 1e-3) {
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

		// �ؼ���ʹ�� DMatrixSparseTriplet �ռ�������� COO ��ʽ��
		// initLength ��������Ϊʵ��Ԫ������������ᵼ��Ƶ�����ݣ������½�4�����ڴ淭��[citation:1]
		DMatrixSparseTriplet tripletMatrix = new DMatrixSparseTriplet(aclfNet.getNoBus(),aclfNet.getNoBranch(), triplets.size());
		for (Triplet t : triplets) {
			tripletMatrix.addItem(t.row, t.col, t.value);
		}

		// ת��Ϊ CSC ��ʽ�����ں�����ѧ���㣩
		DMatrixSparseCSC cscMatrix = DConvertMatrixStruct.convert(tripletMatrix, (DMatrixSparseCSC) null);
//		triplets.forEach(t->{
//			System.out.println(t+","+ cscMatrix.get(t.row, t.col));
//		});
		return cscMatrix;
	}
	
	// ������
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
	 * ����ڵ�֮���ת���迹ϡ����󣨱���sortNumber��Ϊ������
	 * 
	 * @param busSet Դ�ڵ�ID���ϣ�ע������Ľڵ㣩
	 * @param targetBusSet Ŀ��ڵ�ID���ϣ�������ѹ�Ľڵ㣩
	 * @return CSC��ʽϡ�����������ΪԴ�ڵ�sortNumber��������ΪĿ��ڵ�sortNumber
	 */
	public DMatrixSparseCSC calculateTransferImpedanceSparse(Set<String> busSet, Set<String> targetBusSet) {
		// 1. ��ȡԴ�ڵ��б���Ŀ��ڵ��б�
	    List<AclfBus> sourceBuses = aclfNet.getBusList().stream()
	            .filter(bus -> busSet.contains(bus.getId()))
	            .collect(Collectors.toList());
	    
	    List<AclfBus> targetBuses = aclfNet.getBusList().stream()
	            .filter(bus -> targetBusSet.contains(bus.getId()))
	            .collect(Collectors.toList());
	    
	    // 2. �洢��Ԫ�� (row, col, value)
	    List<Triplet> triplets = new ArrayList<>();
	 // ��ÿ��Դ�ڵ㣬ע�뵥λ���������һ�����緽��
        ISparseEqnComplex eqn = aclfNet.formYMatrix();
	    // 3. ����ת���迹
	    for (AclfBus sourceBus : sourceBuses) {
	        int sourceSortNum = sourceBus.getSortNumber();
	        
	       
	        
	        // ��Դ�ڵ�ע�뵥λ������ֱ��ʹ��sortNumber��Ϊ������
	        
	        
	        // ��ȡ��Ŀ��ڵ�ĵ�ѹ����Ϊת���迹��
	        for (AclfBus targetBus : targetBuses) {
	        	int targetSortNum = targetBus.getSortNumber();
	        	eqn.setB2Zero(); // �����������
	            
	            eqn.setBi(new Complex(1.0, 0.0), sourceSortNum);
		        eqn.setBi(new Complex(-1.0, 0.0), targetSortNum);
		        try {
		            eqn.solveEqn(); // ���ڵ��ѹ
		        } catch (IpssNumericException e) {
		            e.printStackTrace();
		            continue;
		        }
	            
		        Complex c = eqn.getX(sourceSortNum).subtract(eqn.getX(targetSortNum));
	            double value = c.getReal();// ȡ��ֵ
	            
	            // ֻ�洢����ֵ����ѡ��
	            if (Math.abs(value) > 1e-6) {
	                triplets.add(new Triplet(sourceSortNum, targetSortNum, value));
	            }
	        }
	    }
	    
	    // 4. ����ϡ���������=�ܽڵ���������=�ܽڵ�����
	    int totalBusCount = aclfNet.getNoBus();
	    DMatrixSparseTriplet tripletMatrix = new DMatrixSparseTriplet(
	        totalBusCount, totalBusCount, triplets.size()
	    );
	    
	    for (Triplet t : triplets) {
	        tripletMatrix.addItem(t.row, t.col, t.value);
	    }
	    
	    // ת��Ϊ CSC ��ʽ�����ں�����ѧ���㣩
	    DMatrixSparseCSC cscMatrix = DConvertMatrixStruct.convert(tripletMatrix, (DMatrixSparseCSC) null);
	    
	    return cscMatrix;
	}

	
}
