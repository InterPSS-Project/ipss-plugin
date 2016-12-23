package org.interpss.dstab.control.gov.custom;


import org.interpss.numeric.datatype.LimitType;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.common.util.IpssLogger;
import com.interpss.common.util.XmlBeanUtil;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.AbstractGovernor;
import com.interpss.dstab.mach.Machine;


/**
 * 
 * @author Wenlong Zhu,  z.huw@foxmail.com
 *
 */
public class PID_SixCoefficientHydroGovernor extends AbstractGovernor {
	
	private double statePm = 0.0, statePref = 0.0, stateX1 = 0.0, stateX2 = 0.0, stateX3 = 0.0, stateX4 = 0.0, stateX5 = 0.0, stateX6 = 0.0;
	private LimitType limit = null;
	
	//private LimitType limit_1 = null;

	public PID_SixCoefficientHydroGovernor(final String id, final String name, final String caty) {
		// TODO Auto-generated constructor stub
				super(id, name, caty);
				// _data is defined in the parent class. However init it here is a MUST
				_data = new PID_SixCoefficientHydroGovernorData();
	}
	
	/**
	 * Get the governor data 
	 * 
	 * @return the data object
	 */
	public PID_SixCoefficientHydroGovernorData getData() {
		return (PID_SixCoefficientHydroGovernorData)_data;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}
	/**
	 *  Init the controller states
	 *  
	 *  @param msg the SessionMsg object
	 */
	@Override
	public boolean initStates(BaseDStabBus<?,?> abus, Machine mach) {
		//limit = new LimitType(getData().getPmax(), getData().getPmin());
		limit = new LimitType(1, 0);
		statePref = getMachine().getPm();
        if (limit.isViolated(statePref)) {
        	IpssLogger.getLogger().severe("Machine initial mechanical power Pm0 violates its governor power limits, " +
        			"machine id: " + getMachine().getId());
        }
		stateX1 = 0.0;
		stateX2 = stateX1;
		stateX3 = stateX2;
		stateX4 = stateX3;
		stateX5 = stateX4;
		stateX6 = stateX5;
		IpssLogger.getLogger().fine("Governor Limit:      " + limit);
		return true;
	}

	
	
	/**
	 * Perform one step d-eqn calculation
	 *  
	 * @param dt simulation time interval
	 * @param method d-eqn solution method
	 *  @param msg the SessionMsg object
	 */	
	@Override
	public boolean nextStep(final double dt, final DynamicSimuMethod method, Machine mach) {
	   
		if (method == DynamicSimuMethod.MODIFIED_EULER) {
			/*
			 *     Step-1 : x(1) = x(0) + dx_dt(1) * dt
			 *     Step-2 : x(2) = x(0) + 0.5 * (dx_dt(2) + dx_dt(1)) * dt
			 */
			final double dX1_dt = cal_dX1_dt(stateX3);
			final double dX2_dt = cal_dX2_dt(stateX2, stateX3);
			final double dX4_dt = cal_dX4_dt(stateX3,stateX4, stateX5);
			final double dX5_dt = cal_dX5_dt(stateX4);
			final double dX6_dt = cal_dX6_dt(stateX5,stateX6);
			
			//final double X1_1 = stateX1 + dX1_dt * dt;
			final double X1_1=stateX1 + dX1_dt * dt;
			final double X2_1 = stateX2 + dX2_dt * dt;
			final double X3_1 = ((getMachine().getSpeed() - getData().getWf())*(getData().getKp()+getData().getKd()/getData().getT1v())+X1_1 + X2_1)/(1+getData().getKp()+getData().getKd()/getData().getT1v());
			final double X4_1 = stateX4 + dX4_dt * dt;
			final double X5_1 = limit.limit(stateX5 + dX5_dt * dt);
			final double X6_1 = stateX6 + dX6_dt * dt;
			//stateX1 = stateX1 + 0.5 * (cal_dX1_dt(X1_1) + dX1_dt) * dt;
			stateX1 = stateX1 + 0.5 * (cal_dX1_dt(X3_1) + dX1_dt) * dt;
			stateX2 = stateX2 + 0.5 * (cal_dX2_dt(X2_1,X3_1) + dX2_dt) * dt;
			stateX3 = ((getMachine().getSpeed() - getData().getWf())*(getData().getKp()+getData().getKd()/getData().getT1v())+stateX1 + stateX2)/(1+getData().getKp()+getData().getKd()/getData().getT1v());;
			stateX4 = stateX4 + 0.5 * (cal_dX4_dt(X3_1,X4_1,X5_1) + dX4_dt) * dt;
			stateX5 = limit.limit(stateX5 + 0.5 * (cal_dX5_dt(X4_1) + dX5_dt) * dt);
			stateX6 = stateX6 + 0.5 * (cal_dX6_dt(X5_1,X6_1) + dX6_dt) * dt;
			return true;
		}
		else if (method == DynamicSimuMethod.RUNGE_KUTTA) {
			// TODO: TBImpl
			return false;
		} else {
			throw new InterpssRuntimeException("SimpleGovernor.nextStep(), invalid method");
		}
	}	
   
	private double cal_dX1_dt(final double X3) {
		return ((getMachine().getSpeed() - getData().getWf())-getData().getBp()*X3)*getData().getKi();
	}
	
	private double cal_dX2_dt(final double X2, final double X3) {
		return (((getMachine().getSpeed() - getData().getWf())-getData().getBp()*X3)*(-getData().getKd()/getData().getT1v())-X2)/getData().getT1v();
	}

	private double cal_dX4_dt(final double X3, final double X4,final double X5) {
		return  ((X3-X5)*getData().getK0()-X4)/getData().getTyb();
	}

	private double cal_dX5_dt(final double X4) {
		return  X4/ getData().getTy();
	}

	private double cal_dX6_dt(final double X5,final double X6) {
		//return ( X4 - X5 ) / getData().getT5();
		 double e=getData().getEqy()*getData().getEh()/getData().getEh()-getData().getEqh();
		return X5*((getData().getEy()/(getData().getEqh()*getData().getTw()))+(getData().getEy()*e/(getData().getEqh()*getData().getEqh()*getData().getTw())))-X6/(getData().getEqh()*getData().getTw());
	}

	/**
	 * Get the controller output
	 * 
	 * @return the output
	 */	
	@Override
	public double getOutput(Machine mach) {
		//return stateX5 * getData().getFp3() + stateX4 * getData().getFp2() + stateX3 * getData().getFp1();
		double e=getData().getEqy()*getData().getEh()/getData().getEh()-getData().getEqh();
		return statePref+(stateX6-(getData().getEy()*e/getData().getEqh())*stateX5);
		//return stateX5-2*stateX4;
	}

	/**
	 * Get the editor panel for controller data editing
	 * 
	 * @return the editor panel object
	 */	
/*	@Override
	public Object getEditPanel() {
		_editPanel.init(this);
		return _editPanel;
	}
	*/
	@Override
	public void setRefPoint(double x) {
		statePref = x;
	}	
} // SimpleExcAdapter

	


