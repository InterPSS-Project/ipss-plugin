package org.interpss.threePhase.dataparser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDynamicDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticDataParser;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSLoadShape;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileBinding;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileType;
import org.interpss.threePhase.qsts.QstsDeviceStatus;
import org.interpss.threePhase.qsts.QstsProfile;
import org.interpss.threePhase.qsts.QstsProfileBinding;
import org.interpss.threePhase.qsts.QstsScheduleData;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.AclfLoad3Phase;
import com.interpss.core.threephase.Static3PBus;
import com.interpss.core.threephase.Static3PBranch;

public class OpenDssTimeSeriesMetadataTest {

	@Test
	void parserFactoriesExposeSeparateStaticAndDynamicTypes() {
		OpenDSSStaticDataParser staticParser = OpenDSSDataParser.forStaticNetwork();
		OpenDSSDynamicDataParser dynamicParser = OpenDSSDataParser.forDynamicNetwork();

		assertTrue(staticParser.isStaticNetworkMode());
		assertFalse(dynamicParser.isStaticNetworkMode());
		assertInstanceOf(OpenDSSStaticDataParser.class, staticParser);
		assertInstanceOf(OpenDSSDynamicDataParser.class, dynamicParser);
		assertThrows(UnsupportedOperationException.class, () -> staticParser.getDistNetwork());
		assertNotNull(dynamicParser.getDistNetwork());
	}

	@Test
	void parsesInlineLoadShapeMultipliers() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();

		assertTrue(parser.getLoadShapeParser().parseLoadShape(
				"New LoadShape.daily npts=3 interval=1 mult=(0.5 1.0 1.5)",
				"testData/feeder", "Master.dss", 10));

		OpenDSSLoadShape shape = parser.getTimeSeriesData().getShapeRegistry().get("daily");
		assertNotNull(shape);
		assertEquals(3, shape.getNpts());
		assertEquals(1.0, shape.getIntervalHours(), 1.0e-12);
		assertArrayEquals(new double[] {0.5, 1.0, 1.5}, shape.getPMult(), 1.0e-12);
		assertArrayEquals(new double[] {0.5, 1.0, 1.5}, shape.getQMult(), 1.0e-12);
	}

	@Test
	void parsesIndependentPAndQMultipliers() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();

		assertTrue(parser.getLoadShapeParser().parseLoadShape(
				"New LoadShape.pq npts=2 sinterval=900 hour=(0 0.25) pmult=(0.8 1.1) qmult=(0.7 1.2)",
				"testData/feeder", "Master.dss", 11));

		OpenDSSLoadShape shape = parser.getTimeSeriesData().getShapeRegistry().get("PQ");
		assertNotNull(shape);
		assertEquals(0.25, shape.getIntervalHours(), 1.0e-12);
		assertArrayEquals(new double[] {0.0, 0.25}, shape.getHour(), 1.0e-12);
		assertArrayEquals(new double[] {0.8, 1.1}, shape.getPMult(), 1.0e-12);
		assertArrayEquals(new double[] {0.7, 1.2}, shape.getQMult(), 1.0e-12);
	}

	@Test
	void parsesCsvLoadShape() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();

		assertTrue(parser.getLoadShapeParser().parseLoadShape(
				"New LoadShape.csv npts=2 csvfile=qsts/loadshape-pq.txt",
				"src/test/resources/opendss", "Master.dss", 12));

		OpenDSSLoadShape shape = parser.getTimeSeriesData().getShapeRegistry().get("csv");
		assertNotNull(shape);
		assertArrayEquals(new double[] {0.0, 1.0}, shape.getHour(), 1.0e-12);
		assertArrayEquals(new double[] {0.9, 1.1}, shape.getPMult(), 1.0e-12);
		assertArrayEquals(new double[] {0.8, 1.2}, shape.getQMult(), 1.0e-12);
	}

	@Test
	void recordsLoadShapeDiagnosticsWithoutFailingStaticImport() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();

		assertTrue(parser.getLoadShapeParser().parseLoadShape(
				"New LoadShape.bad npts=4 interval=1 mult=(1.0 1.1)",
				"testData/feeder", "Master.dss", 13));

		assertEquals(1, parser.getTimeSeriesData().getShapeRegistry().size());
		assertEquals(1, parser.getTimeSeriesData().getDiagnostics().size());
		assertTrue(parser.getTimeSeriesData().getDiagnostics().get(0).getMessage().contains("npts"));
	}

	@Test
	void capturesLoadProfileBindingsWithoutChangingStaticLoad() throws InterpssException {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();

		parser.getLoadParser().parseLoadData(
				"New Load.load1 bus1=bus1.1.2.3 phases=3 conn=wye model=1 kv=12.47 kw=90 kvar=30 daily=daily status=variable");

		OpenDSSProfileBinding binding = parser.getTimeSeriesData().getLoadBinding("LOAD1");
		assertNotNull(binding);
		assertEquals("daily", binding.getShapeId(OpenDSSProfileType.DAILY));
		assertEquals(QstsDeviceStatus.VARIABLE, binding.getStatus());
		assertEquals(1, parser.getStaticNetwork().getBus("bus1").getPhaseLoadList().size());
		assertEquals(30.0, parser.getStaticNetwork().getBus("bus1").getPhaseLoadList().get(0)
				.getInit3PhaseLoad().a_0.getReal(), 1.0e-12);
	}

	@Test
	void staticParserCreatesLoadOnStaticNetworkPhaseView() throws InterpssException {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.getStaticNetwork().setBaseKva(100000.0);

		parser.getLoadParser().parseLoadData(
				"New Load.load1 bus1=bus1.1.2 phases=2 conn=wye model=1 kv=12.47 kw=90 kvar=30 daily=daily");

		Static3PBus bus = parser.getStaticNetwork().getBus("bus1");
		assertNotNull(bus);
		assertEquals(1, bus.getPhaseLoadList().size());
		AclfLoad3Phase load = bus.getPhaseLoadList().get(0);
		assertEquals("load1", load.getId());
		assertEquals(45.0, load.getInit3PhaseLoad().a_0.getReal(), 1.0e-12);
		assertEquals(45.0, load.getInit3PhaseLoad().b_1.getReal(), 1.0e-12);
		assertEquals(0.0, load.getInit3PhaseLoad().c_2.abs(), 1.0e-12);
		assertEquals(1, parser.getTimeSeriesData().getLoadStateStore().size());
	}

	@Test
	void staticParserCreatesLineOnStaticNetworkTopology() throws InterpssException {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();

		parser.getLineParser().parseLineData(
				"New Line.l1 bus1=source.1.2.3 bus2=load.1.2.3 phases=3 r1=0.1 x1=0.2 r0=0.3 x0=0.6 length=1");

		assertNotNull(parser.getStaticNetwork().getBus("source"));
		assertNotNull(parser.getStaticNetwork().getBus("load"));
		assertEquals(1, parser.getStaticNetwork().getBranchList().size());
		Static3PBranch branch = parser.getStaticNetwork().getBranchList().get(0);
		assertEquals("source->load(1)", branch.getId());
		assertEquals("l1", branch.getName());
		assertEquals(PhaseCode.ABC, branch.getPhaseCode());
		assertTrue(branch.isActive());
		assertTrue(branch.getZabc().absMax() > 0.0);
	}

	@Test
	void staticParserCreatesTransformerAndRegControlWithoutDynamicNetwork() throws InterpssException {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();

		parser.getXfrParser().parseTransformerDataOneLine(
				"New Transformer.reg1 phases=3 windings=2 buses=[source.1.2.3 load.1.2.3] "
				+ "conns=[wye wye] kvs=[12.47 12.47] kvas=[500 500] xhl=1 %loadloss=0.1");
		parser.getRegulatorParser().parseRegControlData(
				"New RegControl.creg1 transformer=reg1 winding=2 vreg=120 band=2 ptratio=60");
		parser.getRegulatorParser().applyFixedRegControlRatios();

		assertFalse(parser.hasDistNetwork());
		assertNotNull(parser.getStaticNetwork().getBus("source"));
		assertNotNull(parser.getStaticNetwork().getBus("load"));
		assertEquals(1, parser.getStaticNetwork().getBranchList().size());
		Static3PBranch branch = parser.getStaticNetwork().getBranchList().get(0);
		assertEquals("reg1", branch.getName());
		assertEquals(AclfBranchCode.XFORMER, branch.getBranchCode());
		assertEquals(PhaseCode.ABC, branch.getPhaseCode());
		assertEquals(120.0 * 60.0 * Math.sqrt(3.0), branch.getToTurnRatio(), 1.0e-12);
		assertEquals(1, parser.getRegulatorControls().size());
		assertEquals("reg1", parser.getRegulatorControls().get(0).getBranchName());
		assertFalse(parser.hasDistNetwork());
	}

	@Test
	void staticParserCreatesCapacitorOnStaticNetworkPhaseView() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();

		parser.getCapacitorParser().parseCapDataString(
				"New Capacitor.cap1 bus1=bus1.2 phases=1 kvar=50 kv=2.4");

		Static3PBus bus = parser.getStaticNetwork().getBus("bus1");
		assertNotNull(bus);
		assertEquals(1, bus.getPhaseLoadList().size());
		AclfLoad3Phase load = bus.getPhaseLoadList().get(0);
		assertEquals("cap1", load.getId());
		assertEquals(0.0, load.getInit3PhaseLoad().a_0.abs(), 1.0e-12);
		assertEquals(-50.0, load.getInit3PhaseLoad().b_1.getImaginary(), 1.0e-12);
		assertEquals(0.0, load.getInit3PhaseLoad().c_2.abs(), 1.0e-12);
	}

	@Test
	void staticParserCreatesMiniFeederSourceLineLoadAndCapacitor() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();

		parser.parseFeederData("testData/feeder/OpenDSSCapControlMini", "HighVoltageOpen.dss");

		assertEquals("CapControlHigh", parser.getStaticNetwork().getId());
		Static3PBus source = parser.getStaticNetwork().getBus("source");
		Static3PBus capBus = parser.getStaticNetwork().getBus("capbus");
		assertNotNull(source);
		assertNotNull(capBus);
		assertEquals(AclfGenCode.SWING, source.getGenCode());
		assertEquals(1, parser.getStaticNetwork().getBranchList().size());
		assertEquals(2, capBus.getPhaseLoadList().size());
		assertEquals(1, parser.getCapacitorControls().size());
	}

	@Test
	void staticParserConvertsTopologyAndLoadsToPuWithoutDynamicNetwork() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.parseFeederData("testData/feeder/OpenDSSCapControlMini", "HighVoltageOpen.dss");

		assertTrue(parser.convertActualValuesToPU(10.0));

		assertFalse(parser.hasDistNetwork());
		assertEquals(10000.0, parser.getStaticNetwork().getBaseKva(), 1.0e-12);
		Static3PBranch feeder = parser.getStaticNetwork().getBranchList().get(0);
		assertTrue(feeder.getZabc().absMax() > 0.0);
		Static3PBus capBus = parser.getStaticNetwork().getBus("capbus");
		assertEquals(2, capBus.getPhaseLoadList().size());
		assertEquals(0.09, capBus.getPhaseLoadList().get(0).getInit3PhaseLoad().a_0.getReal(), 1.0e-12);
	}

	@Test
	void convertsOpenDssMetadataToGenericQstsSchedule() throws InterpssException {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.getLoadShapeParser().parseLoadShape(
				"New LoadShape.daily npts=2 minterval=30 mult=(0.5 1.5)",
				"testData/feeder", "Master.dss", 14);
		parser.getLoadParser().parseLoadData(
				"New Load.load1 bus1=bus1.1.2.3 phases=3 conn=wye model=1 kv=12.47 kw=90 kvar=30 daily=daily");

		QstsScheduleData scheduleData = parser.getTimeSeriesData().toQstsScheduleData();
		QstsProfile profile = scheduleData.getProfileRegistry().get("DAILY");
		QstsProfileBinding binding = scheduleData.getProfileBindings().get(0);

		assertNotNull(profile);
		assertEquals(2, profile.getPointCount());
		assertEquals(0.5, profile.getPMultiplierAtIndex(0), 1.0e-12);
		assertEquals("daily", binding.getProfileId("daily"));
		assertEquals("load", binding.getDeviceClass());
	}
}
