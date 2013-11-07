package org.interpss.sample.acsc;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.display.AcscOutFunc;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.BusScCode;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.ScBusVoltageType;
import com.interpss.core.algo.SimpleFaultAlgorithm;
import com.interpss.pssl.simu.IpssAclf;
import com.interpss.pssl.simu.net.IpssAcscNet;
import com.interpss.pssl.simu.net.IpssAcscNet.AcscNetworkDSL;

public class AcscSamplePSSL {

	public static void main(String[] args) {
		IpssCorePlugin.init();
		
		AcscSamplePSSL test = new AcscSamplePSSL();
		
		test.unitVoltTest();

		test.lfVoltTest();
	}
	
	public void unitVoltTest() {
		AcscNetwork faultNet = createTestNet();
		
		System.out.println(faultNet.net2String());
		
	  	SimpleFaultAlgorithm algo = CoreObjectFactory.createSimpleFaultAlgorithm(faultNet);
  		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("2", algo);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
	  	algo.calculateBusFault(fault);
		System.out.println("//////////////////////////////////////////////////////");		
		System.out.println("----------- Fault using UnitVolt ---------------------");		
		System.out.println(AcscOutFunc.faultResult2String(faultNet, algo));		
	}

	public void lfVoltTest() {
		AcscNetwork faultNet = createTestNet();
		IpssAclf.createAclfAlgo(faultNet)                        
		            .lfMethod(AclfMethod.NR)
		            .tolerance(0.0001, UnitType.PU)
		            .runLoadflow();               

		System.out.println(AclfOutFunc.loadFlowSummary(faultNet));
		//System.out.println(netDsl.getAcscNet().net2String());
		
	  	SimpleFaultAlgorithm algo = CoreObjectFactory.createSimpleFaultAlgorithm(faultNet);
	  	algo.setScBusVoltage(ScBusVoltageType.LOADFLOW_VOLT);
  		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("2", algo);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
	  	algo.calculateBusFault(fault);
		System.out.println("//////////////////////////////////////////////////////");		
		System.out.println("----------- Fault using AclfVolt ---------------------");		
		System.out.println(AcscOutFunc.faultResult2String(faultNet, algo));		
	}

	private AcscNetwork createTestNet() {
		AcscNetworkDSL netDsl = IpssAcscNet.createAcscNetwork("Sample AcscNetwork");
		netDsl.baseMva(100.0);

		netDsl.addAcscBus("1", "name-Bus 1")
		            .baseVoltage(13800.0)
		            .loadCode(AclfLoadCode.CONST_P)
		            .load(new Complex(1.6, 0.8), UnitType.PU)
		            .scCode(BusScCode.NON_CONTRI);
		         
		netDsl.addAcscBus("2", "name-Bus 2")
		            .baseVoltage(13800.0)  
		            .loadCode(AclfLoadCode.CONST_P)
		            .load(new Complex(2.0, 1.0), UnitType.PU)
		            .scCode(BusScCode.NON_CONTRI);
		
		netDsl.addAcscBus("3", "name-Bus 3")
        			.baseVoltage(13800.0)  
        			.loadCode(AclfLoadCode.CONST_P)
        			.load(new Complex(3.7, 1.3), UnitType.PU)
        			.scCode(BusScCode.NON_CONTRI);		
		
		netDsl.addAcscBus("4", "name-Bus 4")
        			.baseVoltage(1000.0)  
        			.genCode(AclfGenCode.GEN_PV)
        			.genP_vMag(5.0, UnitType.PU, 1.05, UnitType.PU)
        			.scCode(BusScCode.CONTRIBUTE)
        			.z(new Complex(0.0,0.02), SequenceCode.POSITIVE, UnitType.PU) 
        			.z(new Complex(0.0,0.02), SequenceCode.NEGATIVE, UnitType.PU) 
        			.z(new Complex(0.0,1.0e10), SequenceCode.ZERO, UnitType.PU) 
        			.groundCode("SolidGrounded")
        			.groundZ(new Complex(0.0, 0.0), UnitType.PU);	
		
		netDsl.addAcscBus("5", "name-Bus 5")
        			.baseVoltage(4000.0)  
        			.genCode(AclfGenCode.SWING)
        			.voltageSpec(1.05, UnitType.PU, 5.0, UnitType.Deg)
        			.scCode(BusScCode.CONTRIBUTE)
        			.z(new Complex(0.0,0.02), SequenceCode.POSITIVE, UnitType.PU) 
        			.z(new Complex(0.0,0.02), SequenceCode.NEGATIVE, UnitType.PU) 
        			.z(new Complex(0.0,1.0e10), SequenceCode.ZERO, UnitType.PU) 
        			.groundCode("SolidGrounded")
        			.groundZ(new Complex(0.0, 0.0), UnitType.PU);	
		
		
		netDsl.addAcscBranch("1", "2")
		            .branchCode(AclfBranchCode.LINE)
		            .z(new Complex(0.04, 0.25), UnitType.PU)
		            .shuntB(0.5, UnitType.PU)
		            .z0(new Complex(0.0, 0.7), UnitType.PU);     
		
		netDsl.addAcscBranch("1", "3")
        			.branchCode(AclfBranchCode.LINE)
        			.z(new Complex(0.1, 0.35), UnitType.PU) 
        			.z0(new Complex(0.0,1.0), UnitType.PU);
		
		netDsl.addAcscBranch("2", "3")
        			.branchCode(AclfBranchCode.LINE)
        			.z(new Complex(0.08, 0.3), UnitType.PU)
        			.shuntB(0.5, UnitType.PU)
        			.z0(new Complex(0.0,0.75), UnitType.PU);
		
		netDsl.addAcscBranch("4", "2")
					.branchCode(AclfBranchCode.XFORMER)
					.z(new Complex(0.0, 0.015), UnitType.PU)
					.turnRatio(1.0,  1.05, UnitType.PU)
					.z0( new Complex(0.0, 0.03), UnitType.PU)
					.fromGrounding(XfrConnectCode.WYE_UNGROUNDED)
					.toGrounding(XfrConnectCode.DELTA);
		
		netDsl.addAcscBranch("5", "3")
					.branchCode(AclfBranchCode.XFORMER)
					.z(new Complex(0.0, 0.03), UnitType.PU)
					.turnRatio(1.0,  1.05, UnitType.PU)
					.z0(new Complex(0.0, 0.03), UnitType.PU)
					.fromGrounding(XfrConnectCode.WYE_UNGROUNDED)
					.toGrounding(XfrConnectCode.DELTA);

		//System.out.println(netDsl.getAcscNet().net2String());
		return netDsl.getAclfNet();
	}
}
