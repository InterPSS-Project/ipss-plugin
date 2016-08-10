package org.interpss.dstab.load;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.net.DataCheckConfiguration;
import com.interpss.core.net.NameTag;
import com.interpss.core.net.Network;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.device.DynamicBusDevice;
import com.interpss.dstab.device.DynamicBusDeviceType;
import com.interpss.dstab.dynLoad.DistNetworkEquivalentModel;
import com.interpss.dstab.dynLoad.DynLoadModel;
import com.interpss.dstab.dynLoad.DynLoadVFreqDependentModel;
import com.interpss.dstab.dynLoad.InductionMotor;
import com.interpss.dstab.dynLoad.LD1PAC;
import com.interpss.dstab.mach.Machine;


/**
 * CMPLDW is a composite load characteristic model, it represents a distribution system in a simplified 
 * approach, using distribution equivalent model, three different types of 3-phase induction motors, single-phase air conditioner motor
 * model, power electronic device, and PV based distribution generation. Considering its structure,
 * it cannot be represented as a traditional dynamic load model, which usually involves only one load bus, and is interfaced with the 
 * network using a Northon equivalent. In this model, the actual dynamic load models are connected to the 
 * later-added low-voltage <load bus>. As such, CMPLDW is not a sub-class of DynLoadModel. 
 * 
 * Structure of the CMPLDW model is as follows:
 *                                   load bus
 *                                
 *                                      |--3-Phase motor A
 * sys bus       low-side bus           |--3-Phase motor B
 * |----Xfr(LTC)----|---Equiv feeder----|--3-Phase motor C
 *                                      |--1-Phase A/C motor D
 *                                      |--Power electronic
 *                                      |--Static V/F dependent load
 *                                      |--DG
 *                                      
 * - 3 Phase induction motor is based on MOTORW model
 * - 1 phase A/C motor is represented byLD1PAC model with hard-code parameters:
 *   Tv = 0.02 voltage sensing time constant, sec.
 *   Tf = 0.05 frequency sensing time constant, sec.
 *   Kp1 = 0. real power coefficient for runing state 1, pu W/ pu V
 *   Np1 = 1.0 real power exponent for runing state 1
 *   Kq1 = 6.0 reactive power coefficient for runing state 1, pu VAr/ pu V
 *   Nq1 = 2.0 reactive power exponent for runing state 1 
 *   Kp2 = 12.0 real power coefficient for runing state 2, pu W/ pu V 
 *   Np2 = 3.2 real power exponent for runing state 2 
 *   Kq2 = 11.0 reactive power coefficient for runing state 2, pu VAr/ pu V 
 *   Nq2 = 2.5 reactive power exponent for runing state 2
 *   CmpKpf = 1.0 real power frequency sensitivity, pu W / pu freq.
 *   CmpKqf = -3.3 reactive power frequency sensitivity, pu VAr / pu freq.
 *   Trstrt = 0.4 restart delay time, sec.
 *   Lfadj = 0. stall voltage sensitivity to loading factor
 *
 *
 *
 *  If, during initialization, the far end voltage is computed to be less than 0.95 p.u., Rfdr and Xfdr are reduced to bring it
    above 0.95.
 *   
 *  After the load components are initialized to determine their reactive power consumption, shunt capacitance is added to
    make the Q at the original load bus equal the value from the load flow solution. If Fb = 0., all of this capacitance is added
    at the far end of the feeder (Bf2). If this capacitance is negative (inductive), the substation capacitor (Bss) is reduced to
    make Bf2 zero or slightly positive.
 * 
 *                                      
 *                                      
 *
 */
public interface DynLoadCMPLDW  extends DynamicBusDevice{
	
	void setGroupId(String groupId);
	String getGroupId();
	
	double getMVABase();
	void setMVABase(double mvaBase);
	
	DistNetworkEquivalentModel getDistEquivalent();
	
	double getFmA();
	double getFmB();
	double getFmC();
	double getFmD();
	double getFel();
	
	void setFmA(double motorAFraction);
	void setFmB(double motorBFraction);
	void setFmC(double motorCFraction);
	void setFmD(double motorDFraction);
	void setFel(double electronicLoadFraction);
	
	int getMotorTypeA();
	int getMotorTypeB();
	int getMotorTypeC();
	int getMotorTypeD();
	
	void setMotorTypeA(int motorTypeA);
	void setMotorTypeB(int motorTypeB);
	void setMotorTypeC(int motorTypeC);
	void setMotorTypeD(int motorTypeD);
	
	InductionMotor getInductionMotorA();
	
	InductionMotor getInductionMotorB();
	
	InductionMotor getInductionMotorC();
	
	LD1PAC get1PhaseACMotor();
	
	DynLoadVFreqDependentModel getStaticLoadModel();
	
	DStabBus getLowVoltBus();
	
	DStabBus getLoadBus();
	
	

}
