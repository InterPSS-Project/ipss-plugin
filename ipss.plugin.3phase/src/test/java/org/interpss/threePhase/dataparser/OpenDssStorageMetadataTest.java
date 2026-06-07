package org.interpss.threePhase.dataparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSGeneratorModel;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileBinding;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileType;
import org.junit.jupiter.api.Test;
import org.interpss.numeric.datatype.Unit.UnitType;
import com.interpss.core.threephase.IGen3Phase;
import com.interpss.core.threephase.IPhaseGen;
import com.interpss.core.threephase.Static3PBus;

public class OpenDssStorageMetadataTest {

	@Test
	void parsesDischargingStorageAsPositiveGeneratorInjection() {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		double baseKva = parser.getDistNetwork().getBaseKva();

		assertTrue(parser.getStorageParser().parseStorageData(
				"New Storage.batt1 bus1=bus1.1.2.3 phases=3 conn=wye kv=12.47 kva=500 kwrated=400 kw=250 kvar=40 "
						+ "state=discharging kwhrated=1000 kwhstored=600 %stored=60 %reserve=20 %discharge=50 "
						+ "%effcharge=95 %effdischarge=92 daily=battday",
				"Master.dss", 30));

		DStab3PBus bus = parser.getDistNetwork().getBus("bus1");
		assertNotNull(bus);
		IGen3Phase generator = bus.getContributeGenList().get(0);
		assertEquals(250.0 / baseKva, generator.getPower3Phase(UnitType.PU)
				.a_0.add(generator.getPower3Phase(UnitType.PU).b_1)
				.add(generator.getPower3Phase(UnitType.PU).c_2).getReal(), 1.0e-12);
		assertEquals(40.0 / baseKva, generator.getPower3Phase(UnitType.PU)
				.a_0.add(generator.getPower3Phase(UnitType.PU).b_1)
				.add(generator.getPower3Phase(UnitType.PU).c_2).getImaginary(), 1.0e-12);
		assertEquals(1, parser.getTimeSeriesData().getGeneratorStateStore().size());

		OpenDSSGeneratorModel model = parser.getTimeSeriesData().getGeneratorModel("batt1");
		assertNotNull(model);
		assertEquals("storage", model.getDeviceClass());
		assertEquals("discharging", model.getStorageState());
		assertEquals(400.0, model.getKwRated(), 1.0e-12);
		assertEquals(1000.0, model.getKwhRated(), 1.0e-12);
		assertEquals(600.0, model.getKwhStored(), 1.0e-12);
		assertEquals(60.0, model.getPctStored(), 1.0e-12);
		assertEquals(20.0, model.getPctReserve(), 1.0e-12);
		assertEquals(50.0, model.getPctDischarge(), 1.0e-12);
		assertEquals(95.0, model.getPctEffCharge(), 1.0e-12);
		assertEquals(92.0, model.getPctEffDischarge(), 1.0e-12);

		OpenDSSProfileBinding binding = parser.getTimeSeriesData().getGeneratorBinding("batt1");
		assertEquals("battday", binding.getShapeId(OpenDSSProfileType.DAILY));
	}

	@Test
	void parsesChargingStorageAsNegativeGeneratorInjection() {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		double baseKva = parser.getDistNetwork().getBaseKva();

		assertTrue(parser.getStorageParser().parseStorageData(
				"New Storage.batt1 bus1=bus1.1.2.3 phases=3 kva=500 kw=150 state=charging %charge=25",
				"Master.dss", 31));

		IGen3Phase generator = parser.getDistNetwork().getBus("bus1").getContributeGenList().get(0);
		assertEquals(-150.0 / baseKva, generator.getPower3Phase(UnitType.PU)
				.a_0.add(generator.getPower3Phase(UnitType.PU).b_1)
				.add(generator.getPower3Phase(UnitType.PU).c_2).getReal(), 1.0e-12);
		OpenDSSGeneratorModel model = parser.getTimeSeriesData().getGeneratorModel("batt1");
		assertEquals("charging", model.getStorageState());
		assertEquals(25.0, model.getPctCharge(), 1.0e-12);
	}

	@Test
	void parsesSinglePhaseStorageOntoSelectedPhasePower() {
		OpenDSSDataParser parser = new OpenDSSDataParser();

		assertTrue(parser.getStorageParser().parseStorageData(
				"New Storage.batt1 bus1=bus1.3 phases=1 kva=100 kw=30 kvar=6 state=discharging",
				"Master.dss", 32));

		IGen3Phase generator = parser.getDistNetwork().getBus("bus1").getContributeGenList().get(0);
		assertEquals(0.0, generator.getPower3Phase(UnitType.PU).a_0.abs(), 1.0e-12);
		assertEquals(0.0, generator.getPower3Phase(UnitType.PU).b_1.abs(), 1.0e-12);
		assertEquals(30.0 / parser.getDistNetwork().getBaseKva(),
				generator.getPower3Phase(UnitType.PU).c_2.getReal(), 1.0e-12);
		assertEquals(6.0 / parser.getDistNetwork().getBaseKva(),
				generator.getPower3Phase(UnitType.PU).c_2.getImaginary(), 1.0e-12);
	}

	@Test
	void staticParserCreatesStorageOnStaticNetworkPhaseView() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.getStaticNetwork().setBaseKva(100000.0);

		assertTrue(parser.getStorageParser().parseStorageData(
				"New Storage.batt1 bus1=bus1.3 phases=1 kva=100 kw=30 kvar=6 state=charging",
				"Master.dss", 33));

		Static3PBus bus = parser.getStaticNetwork().getBus("bus1");
		assertNotNull(bus);
		assertEquals(1, bus.getPhaseGenList().size());
		IPhaseGen generator = bus.getPhaseGenList().get(0);
		assertEquals("batt1", generator.getId());
		assertEquals(0.0, generator.getPower3Phase(UnitType.PU).a_0.abs(), 1.0e-12);
		assertEquals(0.0, generator.getPower3Phase(UnitType.PU).b_1.abs(), 1.0e-12);
		assertEquals(-30.0 / parser.getStaticNetwork().getBaseKva(),
				generator.getPower3Phase(UnitType.PU).c_2.getReal(), 1.0e-12);
		assertEquals(6.0 / parser.getStaticNetwork().getBaseKva(),
				generator.getPower3Phase(UnitType.PU).c_2.getImaginary(), 1.0e-12);
		assertEquals(1, parser.getTimeSeriesData().getGeneratorStateStore().size());
	}
}
