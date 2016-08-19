package org.interpss.mapper.odm.impl.dstab;

import org.ieee.odm.schema.DStabLoadDataXmlType;
import org.ieee.odm.schema.DynamicLoadCMPLDWXmlType;
import org.ieee.odm.schema.LoadCharacteristicTypeEnumType;
import org.interpss.dstab.load.DynLoadCMPLDW;
import org.interpss.dstab.load.impl.DynLoadCMPLDWImpl;

import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.device.DynamicBusDevice;

public class DynLoadDataHelper {
	
	private DStabBus bus = null;
	private DStabilityNetwork dynNet = null;
	
	public DynLoadDataHelper() {
	
	}
	
	
	public DynLoadDataHelper(DStabilityNetwork dstabNet) {
		this.dynNet = dstabNet;
		
	}
	
	public DynamicBusDevice createDynLoadModel(DStabLoadDataXmlType dynLoad, DStabBus dstabBus){
		DynamicBusDevice loadModel = null;
		if(dynLoad!=null){
			if(dynLoad.getLoadXmlType()==LoadCharacteristicTypeEnumType.WECC_COMPOSITE_LOAD){
				loadModel = createCMPLDWLoadModel(dynLoad.getLoadModel().getCMPLDWLoad(),dstabBus,dynLoad.getId());
			}
		}
		return loadModel;
	}
	
	private DynLoadCMPLDW createCMPLDWLoadModel(DynamicLoadCMPLDWXmlType cmpldwXml, DStabBus dstabBus, String loadId){
		
		this.bus= dstabBus; 
		
		DynLoadCMPLDW cmpldw = new DynLoadCMPLDWImpl(loadId);
		
		dstabBus.setInfoOnlyDynModel(cmpldw); // CMPLDW is not a model directly used in simulation, its components are modeled and used in simulation
		
		cmpldw.setMVABase(cmpldwXml.getMva());
		
		cmpldw.getDistEquivalent().setBss(cmpldwXml.getBss());
		
		cmpldw.getDistEquivalent().setRfdr(cmpldwXml.getRfdr());
		
		cmpldw.getDistEquivalent().setXfdr(cmpldwXml.getXfdr());
		
		cmpldw.getDistEquivalent().setFb(cmpldwXml.getFb());
		
		cmpldw.getDistEquivalent().setXxf(cmpldwXml.getXxf());
		
		cmpldw.getDistEquivalent().setTfixHS(cmpldwXml.getTfixHS());
		
		cmpldw.getDistEquivalent().setTfixLS(cmpldwXml.getTfixLS());
		
		cmpldw.getDistEquivalent().setLTC((int) cmpldwXml.getLTC());
		
		cmpldw.getDistEquivalent().setTmin(cmpldwXml.getTmin());
		
		cmpldw.getDistEquivalent().setTmax(cmpldwXml.getTmax());
		
		cmpldw.getDistEquivalent().setStep(cmpldwXml.getStep());
		
		cmpldw.getDistEquivalent().setVmin(cmpldwXml.getVmin());
		
		cmpldw.getDistEquivalent().setVmax(cmpldwXml.getVmax());
		
		cmpldw.getDistEquivalent().setTdelay(cmpldwXml.getTdel());
		
		cmpldw.getDistEquivalent().setTtap(cmpldwXml.getTtap());
		
		cmpldw.getDistEquivalent().setTtap(cmpldwXml.getTtap());
		
		cmpldw.getDistEquivalent().setRcomp(cmpldwXml.getRcomp());
		
		cmpldw.getDistEquivalent().setXcomp(cmpldwXml.getXcomp());
		
		// load percentages of the dynamic load component
		
		cmpldw.setFmA(cmpldwXml.getFma());
		cmpldw.setFmB(cmpldwXml.getFmb());
		cmpldw.setFmC(cmpldwXml.getFmc());
		cmpldw.setFmD(cmpldwXml.getFmd());
		cmpldw.setFel(cmpldwXml.getFel());
		
		// motor types
		cmpldw.setMotorTypeA(cmpldwXml.getMtpA());
		cmpldw.setMotorTypeB(cmpldwXml.getMtpB());
		cmpldw.setMotorTypeC(cmpldwXml.getMtpC());
		cmpldw.setMotorTypeD(cmpldwXml.getMtpD());
		
		// Electronic loads
		//TODO
		
		
		// Motor A 
		//cmpldw.setFmA(cmpldwXml.getFma());
		cmpldw.getInductionMotorA().setLoadPercent(cmpldwXml.getFma());
		cmpldw.getInductionMotorA().setLoadFactor(cmpldwXml.getLfmA());
		cmpldw.getInductionMotorA().setRa(cmpldwXml.getRsA());  //Stator resistor
		cmpldw.getInductionMotorA().setXs(cmpldwXml.getLsA()); // Synchronous reactance
		cmpldw.getInductionMotorA().setXp(cmpldwXml.getLpA()); // Transient reactance
		cmpldw.getInductionMotorA().setXpp(cmpldwXml.getLppA()); // Sub-Transient reactance
		cmpldw.getInductionMotorA().setTp0(cmpldwXml.getTpoA()); // Transient open circuit time constant
		cmpldw.getInductionMotorA().setTpp0(cmpldwXml.getTppoA()); // Sub-Transient open circuit time constant
		cmpldw.getInductionMotorA().setH(cmpldwXml.getHA()); // 
		cmpldw.getInductionMotorA().setC(1.0); // assuming Etrq = 2.0; since Tm = (A+B*W+C*W^2)*Tm0
		// all the protections are not implemented at this stage.
		
		
		// Motor B
		
		cmpldw.getInductionMotorB().setLoadPercent(cmpldwXml.getFmb());
		cmpldw.getInductionMotorB().setLoadFactor(cmpldwXml.getLfmB());
		cmpldw.getInductionMotorB().setRa(cmpldwXml.getRsB());  //Stator resistor
		cmpldw.getInductionMotorB().setXs(cmpldwXml.getLsB()); // Synchronous reactance
		cmpldw.getInductionMotorB().setXp(cmpldwXml.getLpB()); // Transient reactance
		cmpldw.getInductionMotorB().setXpp(cmpldwXml.getLppB()); // Sub-Transient reactance
		cmpldw.getInductionMotorB().setTp0(cmpldwXml.getTpoB()); // Transient open circuit time constant
		cmpldw.getInductionMotorB().setTpp0(cmpldwXml.getTppoB()); // Sub-Transient open circuit time constant
		cmpldw.getInductionMotorB().setH(cmpldwXml.getHB()); // 
		cmpldw.getInductionMotorB().setC(1.0); // assuming Etrq = 2.0; since Tm = (A+B*W+C*W^2)*Tm0
		// all the protections are not implemented at this stage.
		
		
		
		// Motor C
		
		
		cmpldw.getInductionMotorC().setLoadPercent(cmpldwXml.getFmc());
		cmpldw.getInductionMotorC().setLoadFactor(cmpldwXml.getLfmC());
		cmpldw.getInductionMotorC().setRa(cmpldwXml.getRsC());  //Stator resistor
		cmpldw.getInductionMotorC().setXs(cmpldwXml.getLsC()); // Synchronous reactance
		cmpldw.getInductionMotorC().setXp(cmpldwXml.getLpC()); // Transient reactance
		cmpldw.getInductionMotorC().setXpp(cmpldwXml.getLppC()); // Sub-Transient reactance
		cmpldw.getInductionMotorC().setTp0(cmpldwXml.getTpoC()); // Transient open circuit time constant
		cmpldw.getInductionMotorC().setTpp0(cmpldwXml.getTppoC()); // Sub-Transient open circuit time constant
		cmpldw.getInductionMotorC().setH(cmpldwXml.getHC()); // 
		cmpldw.getInductionMotorC().setC(1.0); // assuming Etrq = 2.0; since Tm = (A+B*W+C*W^2)*Tm0
		// all the protections are not implemented at this stage.
		
		
		
		// Motor D - single phase induction motor
		
		cmpldw.get1PhaseACMotor().setLoadPercent(cmpldwXml.getFmd());
		cmpldw.get1PhaseACMotor().setPowerFactor(cmpldwXml.getCompPF());
		cmpldw.get1PhaseACMotor().setVstall(cmpldwXml.getVstall());
		cmpldw.get1PhaseACMotor().setRstall(cmpldwXml.getRstall());
		cmpldw.get1PhaseACMotor().setXstall(cmpldwXml.getXstall());
		cmpldw.get1PhaseACMotor().setTstall(cmpldwXml.getTstall());
		cmpldw.get1PhaseACMotor().setFrst(cmpldwXml.getFrst());
		cmpldw.get1PhaseACMotor().setVrst(cmpldwXml.getVrst());
		cmpldw.get1PhaseACMotor().setTrst(cmpldwXml.getTrst());
		cmpldw.get1PhaseACMotor().setFuvr(cmpldwXml.getFuvr());
		cmpldw.get1PhaseACMotor().setVtr1(cmpldwXml.getVtr1());
		cmpldw.get1PhaseACMotor().setTtr1(cmpldwXml.getTtr1());
		cmpldw.get1PhaseACMotor().setVtr2(cmpldwXml.getVtr2());
		cmpldw.get1PhaseACMotor().setTtr2(cmpldwXml.getTtr2());
		cmpldw.get1PhaseACMotor().setVc1off(cmpldwXml.getVc1Off());
		cmpldw.get1PhaseACMotor().setVc2off(cmpldwXml.getVc2Off());
		cmpldw.get1PhaseACMotor().setVc1on(cmpldwXml.getVc1On());
		cmpldw.get1PhaseACMotor().setVc2on(cmpldwXml.getVc2On());
		cmpldw.get1PhaseACMotor().setTth(cmpldwXml.getTth());
		cmpldw.get1PhaseACMotor().setTh1t(cmpldwXml.getTh1T());
		cmpldw.get1PhaseACMotor().setTh2t(cmpldwXml.getTh2T());
		//cmpldw.get1PhaseACMotor().setTv(cmpldwXml.getTv());
		
		/* Some of the parameters of that model are hard-wired to the following
			values:
			Tv = 0.02 voltage sensing time constant, sec.
			Tf = 0.05 frequency sensing time constant, sec.
			Kp1 = 0. real power coefficient for runing state 1, pu W/ pu V
			Np1 = 1.0 real power exponent for runing state 1
			Kq1 = 6.0 reactive power coefficient for runing state 1, pu VAr/ pu V
			Nq1 = 2.0 reactive power exponent for runing state 1 Kp2 = 12.0 real power coefficient
			for runing state 2, pu W/ pu V Np2 = 3.2 real power exponent for runing state 2 Kq2 =
			11.0 reactive power coefficient for runing state 2, pu VAr/ pu V Nq2 = 2.5 reactive power exponent for
			runing state 2
			CmpKpf = 1.0 real power frequency sensitivity, pu W / pu freq.
			CmpKqf = -3.3 reactive power frequency sensitivity, pu VAr / pu freq.
			Trstrt = 0.4 restart delay time, sec.
			Lfadj = 0. stall voltage sensitivity to loading factor
			*/
		
		//TODO
		return cmpldw;
		
		
	}

}
