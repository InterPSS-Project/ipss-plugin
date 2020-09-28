package org.interpss.threePhase.dynamic.model;

import org.apache.commons.math3.complex.Complex;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.dynLoad.DynLoadModel;

public abstract class DynLoadModel1Phase extends DynamicModel1Phase implements DynLoadModel{
	
	
	protected double loadPercent;
	protected double mva;

	protected double loadFactor = 0.85;
	
	protected BaseDStabBus bus;
	
	
	protected Complex equivY = null;
	
	protected Complex nortonCurrInj;
	protected Complex currInj2Net = null;

	protected Complex initLoadPQ = null;
	protected Complex loadPQ;
	protected Complex compensateShuntY;
	
	protected double accumulatedLoadChangeFactor =0.0;
	
	
	public double getLoadPercent() {
		
		return this.loadPercent;
	}

	@Override
	public void setLoadPercent(double percentOfTotalLoad) {
		this.loadPercent = percentOfTotalLoad;
		
	}

	@Override
	public double getMvaBase() {
		
		return this.mva;
	}

	@Override
	public void setMvaBase(double loadMVABase) {
		this.mva = loadMVABase;
		
	}
	
	public double getLoadFactor() {
		return loadFactor;
	}

	public void setLoadFactor(double loadFactor) {
		this.loadFactor = loadFactor;
	}
	

	@Override
	public Complex getPosSeqEquivY() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean changeLoad(double factor) {
		throw new UnsupportedOperationException();
	}
	
	public  Complex getEquivY(){
		return this.equivY ;
	}
	
	
	@Override
	public void setEquivY(Complex value) {
		this.equivY = value;
		
	}

	
	@Override
	public Complex getCompensateShuntY() {
	    return this.compensateShuntY;
		
	}
	
	@Override
	public void setCompensateShuntY(Complex value) {
		this.compensateShuntY = value;
		
	}
	
	@Override
	public Complex getInitLoadPQ() {
	
		return this.initLoadPQ;
	}

	@Override
	public void setInitLoadPQ(Complex initLoadPQ) {
		this.initLoadPQ = initLoadPQ;
		
	}

	@Override
	public Complex getLoadPQ() {

		return this.loadPQ;
		
	}

	@Override
	public void setLoadPQ(Complex loadPQ) {
		this.loadPQ = loadPQ;
	}

	@Override
	public Complex getCurrInj2Net() {
		
		return this.currInj2Net = getNortonCurInj().subtract(getBusPhaseVoltage().multiply( getCompensateShuntY()));
	}

	@Override
	public void setCurrInj2Net(Complex value) {
		this.currInj2Net = value;
		
	}
	
	@Override
	public Complex getNortonCurInj(){
		return this.nortonCurrInj;
	}
 
	@Override
	public void setNortonCurInj(Complex compCurrent){
		this.nortonCurrInj = compCurrent;
	}
	
	@Override
	public double getAccumulatedLoadChangeFactor() {
		
		return this.accumulatedLoadChangeFactor;
	}

	@Override
	public void setAccumulatedLoadChangeFactor(double value) {
		this.accumulatedLoadChangeFactor =value;
		
	}

}
