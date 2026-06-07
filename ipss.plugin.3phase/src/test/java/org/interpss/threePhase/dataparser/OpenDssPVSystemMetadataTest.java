package org.interpss.threePhase.dataparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSGeneratorModel;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileBinding;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileType;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSTemperatureShape;
import org.interpss.threePhase.qsts.InverterCapabilityData;
import org.interpss.threePhase.qsts.InverterGenAdapter;
import org.interpss.threePhase.qsts.QstsControlCurve;
import org.interpss.threePhase.qsts.QstsDeviceStatus;
import org.interpss.threePhase.qsts.QstsProfileBinding;
import org.interpss.threePhase.qsts.QstsScheduleData;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfGenCode;
import org.interpss.numeric.datatype.Unit.UnitType;
import com.interpss.core.threephase.IPhaseGen;
import com.interpss.core.threephase.Static3PBus;

public class OpenDssPVSystemMetadataTest {

	@Test
	void parsesPVSystemIntoGeneratorAndStaticMetadata() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();
		double baseKva = parser.getStaticNetwork().getBaseKva();

		assertTrue(parser.getPVSystemParser().parsePVSystemData(
				"New PVSystem.pv1 bus1=bus1.1.2.3 phases=3 conn=wye kv=12.47 kva=600 pmpp=500 irradiance=0.8 kvar=50 "
						+ "%pmpp=95 temperature=30 %cutin=2 %cutout=1 effcurve=eff1 p-tcurve=pt1 %pmppcurve=pmpp1 "
						+ "kvarlimitcurve=qmax daily=pvday status=variable",
				"Master.dss", 20));

		Static3PBus bus = parser.getStaticNetwork().getBus("bus1");
		assertNotNull(bus);
		assertEquals(AclfGenCode.GEN_PQ, bus.getGenCode());
		assertEquals(1, bus.getPhaseGenList().size());
		IPhaseGen generator = bus.getPhaseGenList().get(0);
		assertEquals("pv1", generator.getId());
		assertEquals(380.0 / baseKva, generator.getPower3Phase(UnitType.PU)
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
		assertEquals(380.0, model.getKw(), 1.0e-12);
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
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();
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
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();

		assertTrue(parser.getPVSystemParser().parsePVSystemData(
				"New PVSystem.pv1 bus1=bus1.2 phases=1 kva=100 kw=60 kvar=15",
				"Master.dss", 22));

		IPhaseGen generator = parser.getStaticNetwork().getBus("bus1").getPhaseGenList().get(0);
		assertEquals(0.0, generator.getPower3Phase(UnitType.PU).a_0.abs(), 1.0e-12);
		assertEquals(60.0 / parser.getStaticNetwork().getBaseKva(),
				generator.getPower3Phase(UnitType.PU).b_1.getReal(), 1.0e-12);
		assertEquals(15.0 / parser.getStaticNetwork().getBaseKva(),
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

	@Test
	void parsesOfficialOpenDssPVSystemExampleCurvesForInverterCapability() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();
		double baseKva = parser.getStaticNetwork().getBaseKva();

		assertTrue(parser.getXYCurveParser().parseXYCurve(
				"New XYCurve.MyPvsT npts=4 xarray=[0 25 75 100] yarray=[1.2 1.0 0.8 0.6]"));
		assertTrue(parser.getXYCurveParser().parseXYCurve(
				"New XYCurve.MyEff npts=4 xarray=[.1 .2 .4 1.0] yarray=[.86 .9 .93 .97]"));
		assertTrue(parser.getLoadShapeParser().parseLoadShape(
				"New Loadshape.MyIrrad npts=24 interval=1 mult=[0 0 0 0 0 0 .1 .2 .3 .5 .8 .9 1.0 1.0 .99 .9 .7 .4 .1 0 0 0 0 0]",
				"", "Examples6.html", 35));
		assertTrue(parser.getTemperatureShapeParser().parseTemperatureShape(
				"New Tshape.MyTemp npts=24 interval=1 temp=[25, 25, 25, 25, 25, 25, 25, 25, 35, 40, 45, 50 60 60 55 40 35 30 25 25 25 25 25 25]",
				"Examples6.html", 39));
		assertTrue(parser.getPVSystemParser().parsePVSystemData(
				"New PVSystem.PV phases=3 bus1=PVbus kV=12.47 kVA=500 irrad=0.8 Pmpp=500 "
						+ "temperature=25 PF=1 effcurve=Myeff P-TCurve=MyPvsT Daily=MyIrrad TDaily=MyTemp",
				"Examples6.html", 48));

		QstsControlCurve pvsTCurve = parser.getTimeSeriesData().getControlCurve("mypvst");
		assertNotNull(pvsTCurve);
		assertEquals(1.0, pvsTCurve.evaluate(25.0), 1.0e-12);
		QstsControlCurve efficiencyCurve = parser.getTimeSeriesData().getControlCurve("myeff");
		assertNotNull(efficiencyCurve);
		assertEquals(0.9566666666666667, efficiencyCurve.evaluate(0.8), 1.0e-12);
		OpenDSSTemperatureShape temperatureShape = parser.getTimeSeriesData().getTemperatureShape("mytemp");
		assertNotNull(temperatureShape);
		assertEquals(24, temperatureShape.getPointCount());
		assertEquals(60.0, temperatureShape.getTemperature()[12], 1.0e-12);

		OpenDSSGeneratorModel model = parser.getTimeSeriesData().getGeneratorModel("pv");
		assertNotNull(model);
		assertEquals("myirrad", model.getDailyShapeId());
		assertEquals("mytemp", model.getDailyTemperatureShapeId());
		assertEquals("myeff", model.getEfficiencyCurveId());
		assertEquals("mypvst", model.getPvsTCurveId());
		assertEquals(382.6666666666667, model.getKw(), 1.0e-9);

		IPhaseGen generator = parser.getStaticNetwork().getBus("pvbus").getPhaseGenList().get(0);
		assertEquals(382.6666666666667 / baseKva, generator.getPower3Phase(UnitType.PU)
				.a_0.add(generator.getPower3Phase(UnitType.PU).b_1)
				.add(generator.getPower3Phase(UnitType.PU).c_2).getReal(), 1.0e-12);
		InverterGenAdapter adapter = parser.getTimeSeriesData().getInverterAdapterStore().get("pv");
		assertNotNull(adapter);
		InverterCapabilityData capability = adapter.getCapabilityData();
		assertEquals(500.0, capability.getRatedKva(), 1.0e-12);
		assertEquals(382.6666666666667, capability.getAvailableActivePowerKw(), 1.0e-9);
		assertEquals(0.0, capability.getCutInPowerKw(), 1.0e-12);
		assertTrue(capability.getMaxReactivePowerKvar() > 300.0);
	}
}
