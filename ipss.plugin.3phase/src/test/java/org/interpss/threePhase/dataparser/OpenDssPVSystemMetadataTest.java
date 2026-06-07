package org.interpss.threePhase.dataparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSGeneratorModel;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileBinding;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileType;
import org.interpss.threePhase.qsts.QstsDeviceStatus;
import org.interpss.threePhase.qsts.QstsProfileBinding;
import org.interpss.threePhase.qsts.QstsScheduleData;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfGenCode;
import org.interpss.numeric.datatype.Unit.UnitType;
import com.interpss.core.threephase.IGen3Phase;
import com.interpss.core.threephase.IPhaseGen;
import com.interpss.core.threephase.Static3PBus;

public class OpenDssPVSystemMetadataTest {

	@Test
	void parsesPVSystemIntoGeneratorAndStaticMetadata() {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		double baseKva = parser.getDistNetwork().getBaseKva();

		assertTrue(parser.getPVSystemParser().parsePVSystemData(
				"New PVSystem.pv1 bus1=bus1.1.2.3 phases=3 conn=wye kv=12.47 kva=600 pmpp=500 irradiance=0.8 kvar=50 "
						+ "%pmpp=95 temperature=30 %cutin=2 %cutout=1 effcurve=eff1 p-tcurve=pt1 %pmppcurve=pmpp1 "
						+ "kvarlimitcurve=qmax daily=pvday status=variable",
				"Master.dss", 20));

		DStab3PBus bus = parser.getDistNetwork().getBus("bus1");
		assertNotNull(bus);
		assertEquals(AclfGenCode.GEN_PQ, bus.getGenCode());
		assertEquals(1, bus.getContributeGenList().size());
		IGen3Phase generator = bus.getContributeGenList().get(0);
		assertEquals("pv1", generator.getId());
		assertEquals(400.0 / baseKva, generator.getPower3Phase(UnitType.PU)
				.a_0.add(generator.getPower3Phase(UnitType.PU).b_1)
				.add(generator.getPower3Phase(UnitType.PU).c_2).getReal(), 1.0e-12);
		assertEquals(50.0 / baseKva, generator.getPower3Phase(UnitType.PU)
				.a_0.add(generator.getPower3Phase(UnitType.PU).b_1)
				.add(generator.getPower3Phase(UnitType.PU).c_2).getImaginary(), 1.0e-12);
		assertEquals(1, parser.getTimeSeriesData().getGeneratorStateStore().size());

		OpenDSSProfileBinding binding = parser.getTimeSeriesData().getGeneratorBinding("PV1");
		assertNotNull(binding);
		assertEquals("pvday", binding.getShapeId(OpenDSSProfileType.DAILY));
		assertEquals(QstsDeviceStatus.VARIABLE, binding.getStatus());

		OpenDSSGeneratorModel model = parser.getTimeSeriesData().getGeneratorModel("pv1");
		assertNotNull(model);
		assertEquals("pvsystem", model.getDeviceClass());
		assertEquals("bus1", model.getBusId());
		assertEquals(400.0, model.getKw(), 1.0e-12);
		assertEquals(50.0, model.getKvar(), 1.0e-12);
		assertEquals(12.47, model.getNominalKV(), 1.0e-12);
		assertEquals("wye", model.getConnection());
		assertEquals(500.0, model.getPmpp(), 1.0e-12);
		assertEquals(0.8, model.getIrradiance(), 1.0e-12);
		assertEquals(95.0, model.getPctPmpp(), 1.0e-12);
		assertEquals(30.0, model.getTemperature(), 1.0e-12);
		assertEquals(2.0, model.getPctCutIn(), 1.0e-12);
		assertEquals(1.0, model.getPctCutOut(), 1.0e-12);
		assertEquals("eff1", model.getEfficiencyCurveId());
		assertEquals("pt1", model.getPvsTCurveId());
		assertEquals("pmpp1", model.getPctPmppCurveId());
		assertEquals("qmax", model.getKvarLimitCurveId());
	}

	@Test
	void convertsPVSystemBindingToGenericQstsSchedule() {
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.getPVSystemParser().parsePVSystemData(
				"New PVSystem.pv1 bus1=bus1.1.2.3 phases=3 kva=600 kw=500 pf=1 yearly=pvyear",
				"Master.dss", 21);

		QstsScheduleData scheduleData = parser.getTimeSeriesData().toQstsScheduleData();
		QstsProfileBinding binding = scheduleData.getProfileBindings().stream()
				.filter(item -> item.getDeviceClass().equals("generator") && item.getDeviceId().equals("pv1"))
				.findFirst()
				.orElseThrow();

		assertEquals("pvyear", binding.getProfileId("yearly"));
	}

	@Test
	void parsesSinglePhasePVSystemOntoSelectedPhasePower() {
		OpenDSSDataParser parser = new OpenDSSDataParser();

		assertTrue(parser.getPVSystemParser().parsePVSystemData(
				"New PVSystem.pv1 bus1=bus1.2 phases=1 kva=100 kw=60 kvar=15",
				"Master.dss", 22));

		IGen3Phase generator = parser.getDistNetwork().getBus("bus1").getContributeGenList().get(0);
		assertEquals(0.0, generator.getPower3Phase(UnitType.PU).a_0.abs(), 1.0e-12);
		assertEquals(60.0 / parser.getDistNetwork().getBaseKva(),
				generator.getPower3Phase(UnitType.PU).b_1.getReal(), 1.0e-12);
		assertEquals(15.0 / parser.getDistNetwork().getBaseKva(),
				generator.getPower3Phase(UnitType.PU).b_1.getImaginary(), 1.0e-12);
		assertEquals(0.0, generator.getPower3Phase(UnitType.PU).c_2.abs(), 1.0e-12);
	}

	@Test
	void staticParserCreatesPVSystemOnStaticNetworkPhaseView() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.getStaticNetwork().setBaseKva(100000.0);

		assertTrue(parser.getPVSystemParser().parsePVSystemData(
				"New PVSystem.pv1 bus1=bus1.2 phases=1 kva=100 kw=60 kvar=15",
				"Master.dss", 23));

		Static3PBus bus = parser.getStaticNetwork().getBus("bus1");
		assertNotNull(bus);
		assertEquals(1, bus.getPhaseGenList().size());
		IPhaseGen generator = bus.getPhaseGenList().get(0);
		assertEquals("pv1", generator.getId());
		assertEquals(0.0, generator.getPower3Phase(UnitType.PU).a_0.abs(), 1.0e-12);
		assertEquals(60.0 / parser.getStaticNetwork().getBaseKva(),
				generator.getPower3Phase(UnitType.PU).b_1.getReal(), 1.0e-12);
		assertEquals(15.0 / parser.getStaticNetwork().getBaseKva(),
				generator.getPower3Phase(UnitType.PU).b_1.getImaginary(), 1.0e-12);
		assertEquals(1, parser.getTimeSeriesData().getGeneratorStateStore().size());
	}
}
