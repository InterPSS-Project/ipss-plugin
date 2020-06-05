package org.interpss.threePhase.dynamic.model;

import org.interpss.numeric.datatype.Complex3x1;

/**
 * There are many aspects that are different from the dynLoadModel, which is mainly defined for positive sequence and 1-phase scenarios.
 * 
 * The differences include, unbalanced 3 phase load, initLoad specification, equivYabc instead of equivY, current Injection in 3 phases 
 * @author Qiuhua
 *
 */
public abstract class DynLoadModel3Phase extends DynamicModel3Phase{

	protected double loadPercent;
	protected double mvaBase;
	
	protected Complex3x1 currInj2Net = null;
	
	Complex3x1 initLoadPQ3phase = null;
	
	
	public void setLoadPercent(double LdPercent){
		this.loadPercent = LdPercent;
	}
	
	public double getLoadPercent(){
		return this.loadPercent;
	}
	
	public void setMVABase(double mva){
		this.mvaBase = mva;
	}
	
	
	public double getMVABase(){
		return this.mvaBase;
	}
	
	public Complex3x1 getInitLoadPQ3Phase() {
		
		return this.initLoadPQ3phase;
	}
	
	public void  setInitLoadPQ3Phase(Complex3x1 initLoad3Phase) {
		
		this.initLoadPQ3phase = initLoad3Phase;
	}
	

}
