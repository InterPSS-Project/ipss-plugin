package org.interpss.plugin.opf.solver.apache.lp;

import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.linear.OpenMapRealVector;
import org.apache.commons.math3.optimization.linear.LinearConstraint;
import org.apache.commons.math3.optimization.linear.Relationship;
import org.interpss.plugin.opf.common.OPFLogger;
import org.interpss.plugin.opf.constraint.OpfConstraint;

import com.interpss.opf.datatype.OpfConstraintType;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;

public class ApacheLpsolveSolverInputBuilder {

	private List<OpfConstraint> cstContainer = null;	
	private Collection<LinearConstraint> constCol = null;
	private int numOfVar = 0;
	public ApacheLpsolveSolverInputBuilder(List<OpfConstraint> cstContainer,
			Collection<LinearConstraint> constCol, int numOfVar){
		this.cstContainer = cstContainer;	
		this.constCol = constCol;
		this.numOfVar = numOfVar;
		
	}
	
	public void buildInput(){
		for( OpfConstraint conIn : cstContainer ) {						
			IntArrayList idx = conIn.getColNo(); 
			DoubleArrayList val = conIn.getVal();
			
			// output constraint to constraint collection
			OpenMapRealVector vec =  new OpenMapRealVector(numOfVar);	
			LinearConstraint con = null;
			OpfConstraintType type = conIn.getCstType();
			try{
				if(type.equals(OpfConstraintType.EQUALITY)){	
					double rh = conIn.getLowerLimit();	
					for (int j = 0; j<idx.size(); j++){
						int colIdx = idx.elements()[j];
						double posVal = val.elements()[j];
						vec.setEntry(colIdx, posVal);
					}
					con = new LinearConstraint(vec, Relationship.EQ, rh);
					
				}else if(type.equals(OpfConstraintType.LARGER_THAN)){
					double[] valRow = val.elements();					
					double rh = conIn.getLowerLimit();
					
					double[] valRow_r = valRow;
					for(int ii =0; ii<idx.size(); ii++){
						valRow_r[ii] = valRow[ii]*(-1);
						int colIdx = idx.elements()[ii];
						double posVal = valRow_r[ii];
						vec.setEntry(colIdx, posVal);
					}
					con = new LinearConstraint(vec, Relationship.LEQ, -rh);					
							
				}else if(type.equals(OpfConstraintType.LESS_THAN)){
					double[] valRow = val.elements();	
					double rh = conIn.getUpperLimit();
					for (int j = 0; j<idx.size(); j++){
						int colIdx = idx.elements()[j];
						double posVal = valRow[j];
						vec.setEntry(colIdx, posVal);
					}
					con = new LinearConstraint(vec, Relationship.LEQ, rh);										
				}
				constCol.add(con);
			}catch (Exception e){
				OPFLogger.getLogger().severe(e.toString()+ " at constraint: "+conIn.getDesc());
				//e.printStackTrace();
			}		
		}		
	}

}
