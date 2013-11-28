package org.interpss.plugin.opf.constraint;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;
import cern.colt.matrix.impl.SparseDoubleMatrix1D;

public class OpfConstraint {
	
	private int id;
	private String description;
	private double UpperLimit;
	private double LowerLimit;
	private double solutionValue;
	//private SparseDoubleMatrix1D vet;
	private IntArrayList colNo;
	private DoubleArrayList val;
	private cstType type;
	public enum cstType{equality, lessThan, largerThan};
		
	
	public void setId(int id){
		this.id = id;
	}

	public int getId(){
		return id;		
	}
	
	public void setDescription(String des){
		this.description = des;
	}
	public String getDescription(){
		return description;
	}
	
	public void setUpperLimit(double ul){
		this.UpperLimit = ul;
	}
	public double getUpperLimit(){
		return this.UpperLimit;
	}
	public double getLowerLimit(){
		return this.LowerLimit;
	}
	public void setLowerLimit(double ul){
		this.LowerLimit = ul;
	}
	
	public double getSolutionValue(double ll){
		return this.solutionValue;
	}
	public void setSolutionValue(double sv){
		this.solutionValue = sv;
	}
	
	public void setColNo(IntArrayList colNo){
		this.colNo = colNo;		
	}
	
	public IntArrayList getColNo(){
		return this.colNo;
	}
	
	public void setVal(DoubleArrayList val){
		this.val = val;
	}
	
	public DoubleArrayList getVal(){
		return this.val ;
	}
	public void setCstType(cstType type){
		this.type = type;
	}
	public cstType getCstType(){
		return this.type;
	}
	
	public OpfConstraint setConstraint(int id, String des, double ul, double ll, 
			cstType type,IntArrayList colNo,DoubleArrayList val){
		OpfConstraint cst = new OpfConstraint();		
		cst.setDescription(des);
		cst.setId(id);
		cst.setLowerLimit(ll);
		cst.setUpperLimit(ul);
		cst.setColNo(colNo);
		cst.setVal(val);
	    cst.setCstType(type);
		return cst;
	}

}
