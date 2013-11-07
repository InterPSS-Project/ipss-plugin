package org.interpss.sample.opf;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.exp.IpssNumericException;

import com.interpss.CoreObjectFactory;
import com.interpss.OpfObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.opf.dclf.DclfOpfGenBus;
import com.interpss.opf.dclf.DclfOpfNetwork;

public class Opf3BusTestNet {
	public static DclfOpfNetwork create3BusNetwork() throws IpssNumericException {
		DclfOpfNetwork net = OpfObjectFactory.createDclfOpfNetwork();
		net.setAnglePenaltyFactor(1.0); 
		
		//create bus1;
		//GenData
		//ID	atNode	FCost	a	b	capL	capU	initMoney
		//1	    1  142.735	10.694	0.00463	20	200	    10000
		  
		DclfOpfGenBus bus1= OpfObjectFactory.createDclfOpfGenBus("Bus1", net);
		bus1.setBaseVoltage(10, UnitType.kV);
		bus1.setAttributes("Bus 1", "");
		bus1.setLoadCode(AclfLoadCode.CONST_P);
		bus1.setLoadP(1.3266);
		bus1.setGenCode(AclfGenCode.SWING);
		bus1.setCapacityLimit(new LimitType(2.0, 0.2)); //in pu
		bus1.setCoeffA(10.694);
		bus1.setCoeffB(0.00463);
		bus1.setFixedCost(142.735);
		
		//bus2
		//2	2	218.335	18.1	0.00612	10	150	10000
		DclfOpfGenBus bus2 = OpfObjectFactory.createDclfOpfGenBus("Bus2", net);
		bus2.setAttributes("Bus 2", "");
		bus2.setBaseVoltage(10, UnitType.kV);
		bus2.setLoadCode(AclfLoadCode.CONST_P);
		bus2.setLoadP(0.4422);
		bus2.setGenCode(AclfGenCode.GEN_PV);
		bus2.setCapacityLimit(new LimitType(1.50, 0.1));
		bus2.setCoeffA(18.1);
		bus2.setCoeffB(0.00612);
		bus2.setFixedCost(218.335);
		
		//bus3
		//3	3	118.821	37.8896	0.01433	5	20	10000
		DclfOpfGenBus bus3 = OpfObjectFactory.createDclfOpfGenBus("Bus3", net);
		bus3.setBaseVoltage(10, UnitType.kV);
		bus3.setAttributes("Bus 3", "");
		bus3.setLoadCode(AclfLoadCode.CONST_P);
		bus3.setLoadP(0.4422);
		bus3.setGenCode(AclfGenCode.GEN_PV);
		bus3.setCapacityLimit(new LimitType(0.2, 0.05));
		bus3.setCoeffA(37.8896);
		bus3.setCoeffB(0.01433);
		bus3.setFixedCost(118.821);
		
		//Create Branches
		/*
		 * //From	To	lineCap	reactance
          1	2	55	0.20
          1	3	55	0.40
          2	3	55	0.25
		 */
		AclfBranch bra1=CoreObjectFactory.createAclfBranch("Branch1", net);
		net.addBranch(bra1, "Bus1", "Bus2");
		bra1.setFromBus(bus1);
		bra1.setToBus(bus2);
		bra1.setZ(new Complex(0,0.20));
		bra1.setRatingMva1(0.55);
		
		//branch2
		AclfBranch bra2=CoreObjectFactory.createAclfBranch("Branch2", net);
		net.addBranch(bra2, "Bus1", "Bus3");
		bra2.setFromBus(bus1);
		bra2.setToBus(bus3);
		bra2.setZ(new Complex(0,0.4));
		bra2.setRatingMva1(0.55);
		
		AclfBranch bra3=CoreObjectFactory.createAclfBranch("Branch3", net);
		net.addBranch(bra3, "Bus2", "Bus3");
		bra3.setFromBus(bus2);
		bra3.setToBus(bus3);
		bra3.setZ(new Complex(0,0.25));
		bra3.setRatingMva1(0.55);
		
		return net;
	}
}
