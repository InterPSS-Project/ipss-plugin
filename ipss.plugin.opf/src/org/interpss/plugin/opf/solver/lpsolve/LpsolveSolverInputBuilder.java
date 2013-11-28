package org.interpss.plugin.opf.solver.lpsolve;

import java.util.List;

import org.interpss.plugin.opf.common.OPFLogger;
import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.plugin.opf.constraint.OpfConstraint.cstType;

import lpsolve.LpSolve;
import lpsolve.LpSolveException;
import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;

public class LpsolveSolverInputBuilder {

	private List<OpfConstraint> cstContainer = null;	
	public LpsolveSolverInputBuilder(List<OpfConstraint> cstContainer){
		this.cstContainer = cstContainer;	
		
	}
	
	public void buildInput(LpSolve lpsolver){
		//Array2DRowRealMatrix mat = new Array2DRowRealMatrix();
		
		for(OpfConstraint con : cstContainer) {						
			IntArrayList idx = con.getColNo(); 
			DoubleArrayList val = con.getVal();
			
			int[] inIdx = new int[idx.size()];
			for (int j = 0; j<idx.size(); j++){
				inIdx[j] = idx.elements()[j]+1;
			}
			
			try{
				cstType type = con.getCstType();
				if(type.equals(cstType.equality)){	
					double rh = con.getLowerLimit();					
					lpsolver.addConstraintex( idx.size(), val.elements(), inIdx, LpSolve.EQ, rh);
				}else if(type.equals(cstType.largerThan)){
					double[] valRow = val.elements();					
					double rh = con.getLowerLimit();
					if(idx.size()==1 && valRow[0]==1){
						lpsolver.setLowbo(inIdx[0], rh);
					}else{
						double[] valRow_r = valRow;
						for(int ii =0; ii<valRow.length; ii++){
							valRow_r[ii] = valRow[ii]*(-1);
						}
						lpsolver.addConstraintex( idx.size(), valRow_r, inIdx, LpSolve.LE, -rh);
						//lpsolver.addConstraintex( idx.size(), valRow, inIdx, LpSolve.GE, rh);
					}					
				}else if(type.equals(cstType.lessThan)){
					double[] valRow = val.elements();	
					double rh = con.getUpperLimit();
					if(idx.size()==1 && valRow[0]==1){
						lpsolver.setUpbo(inIdx[0], rh);
					}else{
						lpsolver.addConstraintex( idx.size(), val.elements(), inIdx, LpSolve.LE, rh);
					}											
				}
			}catch (LpSolveException e) {				
				OPFLogger.getLogger().severe(e.toString());
			}	
		}	
	}
}
