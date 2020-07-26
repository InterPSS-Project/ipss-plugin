package org.interpss.threePhase.dynamic.model;

import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.DStab3PBus;
import org.interpss.threePhase.dynamic.IDynamicModel1Phase;

import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.net.Network;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;
import com.interpss.dstab.mach.Machine;

public abstract class DynamicModel1Phase extends DynamicBusDeviceImpl implements
		IDynamicModel1Phase {
	
	protected  PhaseCode     connectPhase = null;
	protected  DStab3PBus parentBus = null;
	
    public DStab3PBus getParentBus(){
    	if(this.getDStabBus() instanceof DStab3PBus)
    		return (DStab3PBus)this.getDStabBus();
    	else
    		throw new Error("The parentBus is not a Bus3Phase type, #"+this.getDStabBus().getId());
    }
	
	@Override
	public PhaseCode getPhase() {
		return this.connectPhase;
	}

	@Override
	public void setPhase(PhaseCode p) {
		this.connectPhase = p;

	}
	
	public Complex getBusPhaseVoltage(){
		Complex3x1 vabc = ((DStab3PBus)this.getDStabBus()).get3PhaseVotlages();
		
		switch(this.connectPhase){
		case A: 
			return vabc.a_0;
		case B:
			return vabc.b_1;
		default:
			return vabc.c_2;
		}
		
	}
	
	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public boolean initStates() {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public boolean initStates(BaseDStabBus abus) {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public boolean initStates(BaseDStabBus abus, Machine mach) {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public boolean nextStep(double dt, DynamicSimuMethod method) {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public boolean nextStep(double dt, DynamicSimuMethod method, Machine mach) {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public boolean nextStepMechanical(double dt, DynamicSimuMethod method, Network net) {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public boolean nextStepElectrical(double dt, DynamicSimuMethod method, Network net) {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated NOT
	 */
	public Hashtable<String, Object> getStates(Object ref) {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated NOT
	 */
	public Hashtable<String, Object> getStates(Machine mach, Object ref) {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public double getOutput() {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public double getOutput(Machine mach) {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public Object getOutputObject() {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public List<?> getOutputList() {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public boolean updateAttributes(boolean netChange) {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

}
