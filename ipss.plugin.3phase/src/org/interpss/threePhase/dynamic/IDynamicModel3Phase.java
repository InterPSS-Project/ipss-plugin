package org.interpss.threePhase.dynamic;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab3PBus;

import com.interpss.core.net.ref.BusRef;
import com.interpss.dstab.algo.IDynamicSimulation;
import com.interpss.dstab.device.DynamicBusDevice;
import com.interpss.dstab.device.DynamicDevice;

public interface IDynamicModel3Phase extends DynamicBusDevice{
		
		
		/**
		 * directly set  3x3 impedance matrix on model MVA base
		 * @param ZAbc -3x3 impedance matrix on model MVA base
		 */ 
		public  void setZabc(Complex3x3 ZAbc) ;
		
		/**
		 * set the 3x3 impedance matrix using three-sequence impedances
		 * 
		 * @param z1  -generator positive sequence impedance on machine MVA base
		 * @param z2  -generator negative sequence impedance on machine MVA base
		 * @param z0  -generator zero sequence impedance on machine MVA base
		 */
		public  void setZ120(Complex z1, Complex z2,Complex z0) ;
		
		/**
		 * 
		 * @param machineMVABase   true for returning the value on machine base, otherwise, return it on system base
		 * @return
		 */
		public Complex3x3 getZabc(boolean machineMVABase);
		
		/**
		 * 
		 * @param machineMVABase   true for returning the value on machine base, otherwise, return it on system base
		 * @return
		 */
		public Complex3x3 getYabc(boolean machineMVABase);
		
		/**
		 * 
		 * @return EquivYabc -- the equivalent Yabc of this model used in forming the YABC matrix of the system in dynamic simulation
		 */
		public Complex3x3 getEquivYabc();
		
		/**
		 * Power = VABC*conj(IgenABC-YgenABC*VABC)
		 * If unit = pu, it is on system MVA base
		 * @param unit
		 * @return
		 */
		public Complex3x1 getPower3Phase(UnitType unit);

		
		/**
		 * The dynamic model is represented as A three-phase Norton equivalent. 
		 * return the three phase  Norton equivalent current source at the terminal on system basis
		 * @return
		 */
		public Complex3x1 getISource3Phase();
		
		/**
		 *  
		 * @return the three phase  current injected from this model into the network on system basis
		 */
		public Complex3x1 getIinj2Network3Phase();

}
