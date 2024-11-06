package org.interpss.threePhase.dynamic.model;

import java.util.Hashtable;
import java.util.List;

import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.IDynamicModel3Phase;

import com.interpss.core.net.Network;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;
import com.interpss.dstab.mach.Machine;

public abstract class DynamicModel3Phase extends DynamicBusDeviceImpl implements
		IDynamicModel3Phase {

    protected  DStab3PBus parentBus = null;
	protected  Complex3x3 equivYabc = new Complex3x3();


	public DStab3PBus getParentBus(){
		if(parentBus != null) {
			return parentBus;
		} else{
			if(this.getDStabBus() instanceof DStab3PBus) {
				return parentBus = (DStab3PBus) this.getDStabBus();
			}
		}
		return null;

	}

	@Override
	public Complex3x3 getEquivYabc(){
		 equivYabc = getYabc(false);
		if(equivYabc ==null) {
			equivYabc = new Complex3x3();
		}
		return equivYabc;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
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
	@Override
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
	@Override
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
	@Override
	public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public boolean nextStep(double dt, DynamicSimuMethod method, Machine mach, int flag) {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public boolean nextStepMechanical(double dt, DynamicSimuMethod method, Network net, int flag) {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public boolean nextStepElectrical(double dt, DynamicSimuMethod method, Network net, int flag) {
		// TODO: implement this method
		// Ensure that you remove @generated or mark it @generated NOT
		throw new UnsupportedOperationException();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated NOT
	 */
	@Override
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
	@Override
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
	@Override
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
	@Override
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
	@Override
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
	@Override
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
	@Override
	public boolean updateAttributes(boolean netChange) {
		return true;
	}

}
