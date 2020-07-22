 /*
  * @(#)PiecewiseAlgo012Impl.java   
  *
  * Copyright (C) 2006-2016 www.interpss.org
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
  * @Date 04/15/2016
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.piecewise.seq012.algo;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.matrix.ComplexMatrixEqn;
import org.interpss.numeric.matrix.MatrixUtil;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.piecewise.base.BaseCuttingBranch;
import org.interpss.piecewise.base.BaseSubArea;
import org.interpss.piecewise.base.impl.AbstractPiecewiseAlgoAdapter;
import org.interpss.piecewise.seq012.SubNetwork012;

import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscGen;
import com.interpss.core.acsc.AcscLoad;
import com.interpss.core.acsc.BaseAcscBus;
import com.interpss.core.acsc.BaseAcscNetwork;
import com.interpss.core.acsc.SequenceCode;

/**
 * Piecewise algorithm implementation for 012 sequence network. We use
 * an array[3] to store 012 quantities in the sequence of [0, 1, 2]
 * 
 * @author Mike
 *
 */
public class PiecewiseAlgo012Impl<TBus extends BaseAcscBus<? extends AcscGen, ? extends AcscLoad>, 
								  TNet extends BaseAcscNetwork<TBus,?>, 
								  TSub extends BaseSubArea<ISparseEqnComplex[], Complex3x1[][], Complex3x1>> 
					extends AbstractPiecewiseAlgoAdapter<TBus, TNet, Complex3x1, TSub> {
	// AclfNetwork object
	//private AcscNetwork net;
	
	// Equivalent 012 Z-matrix for cutting branch current calculation
	private ComplexMatrixEqn[] equivZMatrixEqn = new ComplexMatrixEqn[3];
	
	/**
	 * Constructor
	 * 
	 * @param net AcscNetwork object
	 */
	public PiecewiseAlgo012Impl(TNet net) {
		super();
		this.parentNet = net;
	}

	/**
	 * Constructor
	 * 
	 * @param net AcscNetwork object
	 * @param subAreaList SubArea/Network object list
	 */
	public PiecewiseAlgo012Impl(TNet net, List<TSub> subAreaNetList) {
		super();
		this.parentNet = net;
		this.subAreaNetList = subAreaNetList;
	}

	@Override
	public void buildNortonEquivNet(Function<TBus, Complex3x1> injCurrentFunc) throws IpssNumericException {
  		for (TSub subarea: this.subAreaNetList) {
  			// calculate open circuit SubArea/Network voltage
  			solveSubAreaNet(subarea.getFlag(), injCurrentFunc);
  	  		//System.out.println("y1: \n" + y1.toString());
  			
  			// cache bus voltage stored in the subarea Y-matrix sparse eqn into the hashtable
  	  		parentNet.getBusList().forEach(bus -> {
  	  			if (bus.getSubAreaFlag() == subarea.getFlag()) {
  	  				subarea.getBusVoltage().put(bus.getId(), new Complex3x1(
  	  						subarea.getYSparseEqn()[Complex3x1.Index_0].getX(bus.getSortNumber()),
  	  						subarea.getYSparseEqn()[Complex3x1.Index_1].getX(bus.getSortNumber()),
  	  						subarea.getYSparseEqn()[Complex3x1.Index_2].getX(bus.getSortNumber())));
  	  			}
  	  		}); 
  	  		
			formSubAreaZMatrix(subarea);
  		}
	}
	
	/**
	 * Solve for SubArea/Network open circuit bus voltage. The bus injection current is calculated
	 * using the injCurrentFunc - injection current calculation function. The bus voltage results are stored in the subarea
	 * Y-matrix sparse eqn object.
	 * 
	 * @param areaFlag subarea flag
	 * @param injCurrentFunc injection current calculation function
	 * @throws IpssNumericException
	 */
	private void solveSubAreaNet(int areaFlag, Function<TBus,Complex3x1> injCurrentFunc) throws IpssNumericException {
		TSub subArea = this.getSubArea(areaFlag);

		// form Y-matrix (012) for each SubArea/Network
		if (this.netYmatrixDirty) {
  			// there is no need to form the Y matrix if it has not changed
  			if (subArea instanceof SubNetwork012)
  				((SubNetwork012)subArea).formYMatrix();
  			else	
  				subArea.setYSparseEqn( new ISparseEqnComplex[] {
  						parentNet.formScYMatrix(SequenceCode.ZERO, subArea.getFlag()),
  						parentNet.formScYMatrix(SequenceCode.POSITIVE, subArea.getFlag()),		
  						parentNet.formScYMatrix(SequenceCode.NEGATIVE, subArea.getFlag())
  					});
  			
  	  		for ( ISparseEqnComplex eqn : subArea.getYSparseEqn())
  	  			eqn.factorization(1.0e-10);
  		}
  		//for ( ISparseEqnComplex eqn : subArea.getYSparseEqn())
  		//	System.out.println(eqn.toString());
  		
		// set bus injection current to the [b] vector in the [A][x] = [b] eqn
  		parentNet.getBusList().forEach(bus -> {
				if (bus.getSubAreaFlag() == areaFlag) {
					// we use the function to calculate the bus injection current
					Complex3x1 cur = injCurrentFunc.apply(bus);
					subArea.getYSparseEqn()[Complex3x1.Index_0].setBi(cur.a_0, bus.getSortNumber());
					subArea.getYSparseEqn()[Complex3x1.Index_1].setBi(cur.b_1, bus.getSortNumber());
					subArea.getYSparseEqn()[Complex3x1.Index_2].setBi(cur.c_2, bus.getSortNumber());
				}
			});
  		
  		// solve the [A][x] = [b] eqn for 012 seq 
  		for ( ISparseEqnComplex eqn : subArea.getYSparseEqn())
  			eqn.solveEqn();	
	}	

	
	/**
	 * calculate subarea interface bus z-matrix (012). The matrix is stored in
	 * SubArea.zMatrix field.
	 * 
	 * @param subarea the subarea object
	 * @throws IpssNumericException
	 */
	private void formSubAreaZMatrix(TSub subarea) throws IpssNumericException {
		int areaNodes = subarea.getInterfaceBusIdList().size();
		subarea.setZMatrix(new Complex3x1[areaNodes][areaNodes]);
		for (int i = 0; i < areaNodes; i++) {
			int row = parentNet.getBus(subarea.getInterfaceBusIdList().get(i)).getSortNumber();
			if (!subarea.getYSparseEqn()[Complex3x1.Index_0].getBusId(row).equals(subarea.getInterfaceBusIdList().get(i))) {
				// the y-matrix row number and the bus.sortNumber should match
				throw new IpssNumericException("Programming error: PiecewiseAlgorithm.subAreaZMatrix()");
			}

			for ( ISparseEqnComplex eqn : subarea.getYSparseEqn()) {
				eqn.setB2Unity(row);
				eqn.solveEqn();
			}
			
			for (int j = 0; j < areaNodes; j++) {
				int col = parentNet.getBus(subarea.getInterfaceBusIdList().get(j)).getSortNumber();
				subarea.getZMatrix()[j][i] = new Complex3x1(
						subarea.getYSparseEqn()[Complex3x1.Index_0].getX(col),
						subarea.getYSparseEqn()[Complex3x1.Index_1].getX(col),
						subarea.getYSparseEqn()[Complex3x1.Index_2].getX(col));
			}
		}
	}
	
	@Override
	public void calculateCuttingBranchCurrent(BaseCuttingBranch<Complex3x1>[] cuttingBranches)
			throws IpssNumericException {
		// calculate voltage difference across the cutting branches
		Complex3x1[] eCutBranch3x1 = cuttingBranchOpenCircuitVoltage(cuttingBranches); 
		
		// we build the equiv Z only if the network Y matrix has changed
		if (this.netYmatrixDirty) {
			Complex3x1[][] equivZMatrix = buildEquivZMtrix(cuttingBranches);
			// transform Complex3x1 to Complex[3]
			Complex[][][] matrix = MatrixUtil.toComplexMatrix(equivZMatrix);
			// compute inverse of the Z-matrix (012) 
			for ( int i = 0; i < 3; i++ ) {
				this.equivZMatrixEqn[i] = new ComplexMatrixEqn(matrix[i]);
				this.equivZMatrixEqn[i].inverseMatrix();
			}
		}
		
		// transfer Complex3x1 to Complex[3]
		Complex[][] eCutBranchAry = MatrixUtil.toComplexAry(eCutBranch3x1);
		// solve branch current [I] = [Z]-1 [V] for 012 seq
    	Complex[] curAry0 = this.equivZMatrixEqn[0].solveEqn(eCutBranchAry[0], false);	
    	Complex[] curAry1 = this.equivZMatrixEqn[1].solveEqn(eCutBranchAry[1], false);	
    	Complex[] curAry2 = this.equivZMatrixEqn[2].solveEqn(eCutBranchAry[2], false);	
    	// cache the branch current 
    	for (int i = 0; i < cuttingBranches.length; i++) {
    		//System.out.println("Branch cur: " + cuttingBranches[i] + " " + curAry3x1[i]);
    		cuttingBranches[i].setCurrent(new Complex3x1(curAry0[i], curAry1[i], curAry2[i]));
    	}
	}
	
	/**
	 * calculate open circuit voltage difference for the cutting branches. 
	 * Pre-condition: the network bus open-circuit voltage has been calculated
	 * and stored in the netVoltage hashtable. 
	 * 
	 * @param cuttingBranches cutting branch objects
	 * @return cutting branch voltage difference array
	 */
	private Complex3x1[] cuttingBranchOpenCircuitVoltage(BaseCuttingBranch<Complex3x1>[] cuttingBranches) {
		int nCutBranches = cuttingBranches.length;
		Complex3x1[] eCutBranch3x1 = new Complex3x1[nCutBranches];    // transport[M] x open circuit[E]
  		for ( int i = 0; i < nCutBranches; i++) {
  			AcscBranch branch = parentNet.getBranch(cuttingBranches[i].getBranchId());
  			// assume branch current from bus -> to bus
  			// current positive direction - into the sub-area network
  			TSub fromSubArea = this.getSubArea(branch.getFromBus().getSubAreaFlag());
  			TSub toSubArea = this.getSubArea(branch.getToBus().getSubAreaFlag());
  			eCutBranch3x1[i] = fromSubArea.getBusVoltage().get(branch.getFromBus().getId())
  					        	.subtract(toSubArea.getBusVoltage().get(branch.getToBus().getId()));
  			//System.out.println("E open: " + i + ", " + eCutBranch3x1[i]);
  		}
  		return eCutBranch3x1;
	}
	
	/**
	 * build equivalent z-matrix for solving cutting branch current
	 * 
	 * @param cuttingBranches cutting branches
	 * @return the z-matrix
	 */
	private Complex3x1[][] buildEquivZMtrix(BaseCuttingBranch<Complex3x1>[] cuttingBranches)  throws IpssNumericException {
		int nCutBranches = cuttingBranches.length;
		Complex3x1[][] matrix3x1 = new Complex3x1[nCutBranches][nCutBranches];
	  	
		/*
		 * first add the cutting branch [Zl] part
		 */
		for ( int i = 0; i < nCutBranches; i++) {
  			AcscBranch branch = parentNet.getBranch(cuttingBranches[i].getBranchId());
  			for (int j = 0; j < nCutBranches; j++) {
  				matrix3x1[i][j] = i == j? 
  						new Complex3x1(	branch.getZ0(), branch.getZ(), branch.getZ()) : 
  						new Complex3x1();
  			}
		}
		
		/*
		 * process the transpose[Mi]x[Zi]x[M] part.
		 */
		for (TSub subarea : this.subAreaNetList) {
			/*
			 * form Mi matrix. Mi is the connection relationship matrix for
			 * the subarea interface bus and the cutting branch for SubArea i 
			 */
			int[][] M = new int[subarea.getInterfaceBusIdList().size()][nCutBranches];
			for ( int i = 0; i < nCutBranches; i++) {
	  			AcscBranch branch = parentNet.getBranch(cuttingBranches[i].getBranchId());
	  			for (int j = 0; j < subarea.getInterfaceBusIdList().size(); j++)
	  				/*
	  				 * branch current positive direction fromBus -> toBus
	  				 * network injection current positive direction : into the network
	  				 */
	  				M[j][i] = branch.getFromBus().getId().equals(subarea.getInterfaceBusIdList().get(j))? -1 : 
	  							(branch.getToBus().getId().equals(subarea.getInterfaceBusIdList().get(j))? 1: 0);
			}
			
			/*
			 * add the transpose[Mi]x[Zi]x[M] part. 
			 */
			matrix3x1 = MatrixUtil.add(matrix3x1, MatrixUtil.prePostMultiply(subarea.getZMatrix(), M));
		}	
		
		return matrix3x1;
	}
	
	@Override
	public void calcuateSubAreaNetVoltage(BaseCuttingBranch<Complex3x1>[] cuttingBranches) throws IpssNumericException {
		for(TSub subarea : this.subAreaNetList)
			calcuateSubAreaVoltage(subarea, cuttingBranches);  		
	}
	
	/**
	 * calculate the bus voltage for the subarea based on the cutting branch current and the bus open circuit voltage,
	 * according to the superposition principle.
	 * 
	 * @param subArea subarea
	 * @param cuttingBranches cutting branch storing the branch current
	 * @throws IpssNumericException
	 */
	private void calcuateSubAreaVoltage(TSub subArea, BaseCuttingBranch<Complex3x1>[] cuttingBranches)  throws IpssNumericException {
  		// calculate the cutting branch current injection
		ISparseEqnComplex[] yMatrixAry = subArea.getYSparseEqn();
		for (ISparseEqnComplex yMatrix : yMatrixAry)
			yMatrix.setB2Zero();
		
  		for (int cnt = 0; cnt < cuttingBranches.length; cnt++) {
  			AcscBranch branch = parentNet.getBranch(cuttingBranches[cnt].getBranchId());
  			// current into the network as the positive direction
  			if (branch.getFromBus().getSubAreaFlag() == subArea.getFlag()) {
  				yMatrixAry[Complex3x1.Index_0].addToB(cuttingBranches[cnt].getCurrent().a_0.multiply(-1.0), branch.getFromBus().getSortNumber());
  				yMatrixAry[Complex3x1.Index_1].addToB(cuttingBranches[cnt].getCurrent().b_1.multiply(-1.0), branch.getFromBus().getSortNumber());
  				yMatrixAry[Complex3x1.Index_2].addToB(cuttingBranches[cnt].getCurrent().c_2.multiply(-1.0), branch.getFromBus().getSortNumber());
  			}
  			else if (branch.getToBus().getSubAreaFlag() == subArea.getFlag()) {
  				yMatrixAry[Complex3x1.Index_0].addToB(cuttingBranches[cnt].getCurrent().a_0, branch.getToBus().getSortNumber());
  				yMatrixAry[Complex3x1.Index_1].addToB(cuttingBranches[cnt].getCurrent().b_1, branch.getToBus().getSortNumber());
  				yMatrixAry[Complex3x1.Index_2].addToB(cuttingBranches[cnt].getCurrent().c_2, branch.getToBus().getSortNumber());
  			}
  		}
  		//System.out.println(yMatrix);
  		
  		// calculate sub-area bus voltage due to the cutting branch current injection
  		for ( ISparseEqnComplex eqn : subArea.getYSparseEqn())
  			eqn.solveEqn();
  		//System.out.println(ySubArea);
  		
  		// update the bus voltage based on the superposition principle
  		for (int i = 0; i < yMatrixAry[0].getDimension(); i++) {
  			String id = yMatrixAry[0].getBusId(i);
  			Complex v0 = subArea.getBusVoltage().get(id).a_0.add(yMatrixAry[Complex3x1.Index_0].getX(i));
  			Complex v1 = subArea.getBusVoltage().get(id).b_1.add(yMatrixAry[Complex3x1.Index_1].getX(i));
  			Complex v2 = subArea.getBusVoltage().get(id).c_2.add(yMatrixAry[Complex3x1.Index_2].getX(i));
  			//System.out.println("area1 voltage update -- " + id + ", " + netVoltage.get(id) + ", " + yAreaNet1.getX(i) + ", " + v.abs());
  			subArea.getBusVoltage().put(id, new Complex3x1(v0, v1, v2));
  		}		
	}	
}

