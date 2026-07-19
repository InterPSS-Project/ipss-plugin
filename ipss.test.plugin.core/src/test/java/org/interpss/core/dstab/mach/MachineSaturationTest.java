package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.mach.Eq1Ed1Machine;
import com.interpss.dstab.mach.MachineModelType;
import com.interpss.dstab.util.sample.SampleDStabCase;

public class MachineSaturationTest extends TestSetupBase {
	@Test
	public void test_Case1()  throws InterpssException {
		// create a two-bus network. Loadflow calculated
		BaseDStabNetwork<?,?> net = SampleDStabCase.createDStabTestNet();

		// create a machine and connect to the bus "Gen"
		Eq1Ed1Machine mach = (Eq1Ed1Machine)DStabObjectFactory.
							createMachine("MachId", "MachName", MachineModelType.EQ1_ED1_MODEL, net, "Gen", "G1");
		BaseDStabBus<?,?> bus = net.getDStabBus("Gen");

		// set machine data
		mach.setRating(100, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(1000.0);
		mach.calMultiFactors();
		mach.setH(5.0);
		mach.setD(0.01);
		mach.setXd(1.81);
		mach.setXl(0.15);
		mach.setXq(1.08);
		mach.setXd1(0.23);
		mach.setXq1(0.23);
		mach.setX0(0.1);
		mach.setX2(0.2);
		mach.setRa(0.003);
		mach.setTd01(5.6);
		mach.setTq01(1.5);
		mach.setSliner(0.8);  
		mach.setSe100(12.5);
		mach.setSe120(50.0);		
		
		// without considering Ra in calculation of voltageBehindXl, Xds = 1.444036
		// with Ra considered, Xds = 1.4398834500687638
		//System.out.println(mach.getXdAdjusted());
		assertTrue(Math.abs(mach.getXdAdjusted()-1.4398834500687638) < 0.0001);

		bus.setVoltage(new Complex(1.4, 0.0));
		//System.out.println(mach.getXdAdjusted());
		assertTrue(Math.abs(mach.getXdAdjusted()-0.896989852998746) < 0.0001);
	}
}
