package org.interpss.threePhase.dataparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticGenerator;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSGeneratorModel;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSLoadShape;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileBinding;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileType;
import org.interpss.threePhase.qsts.QstsProfileBinding;
import org.interpss.threePhase.qsts.QstsProfile;
import org.interpss.threePhase.qsts.QstsControlMode;
import org.interpss.threePhase.qsts.QstsMode;
import org.interpss.threePhase.qsts.QstsScheduleData;
import org.interpss.threePhase.qsts.QstsStateApplier;
import org.interpss.threePhase.qsts.QstsStepContext;
import org.junit.jupiter.api.Test;

import org.apache.commons.math3.complex.Complex;

import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.threephase.AclfGen3Phase;
import com.interpss.core.threephase.Static3PBus;

public class OpenDssGeneratorMetadataTest {
	@Test
	void parsesIeee8500StylePvGeneratorDutyShapeBinding() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();
		double baseKva = parser.getStaticNetwork().getBaseKva();

		assertTrue(parser.getGeneratorParser().parseGeneratorData(
				"New generator.G1 Bus1=m1026866 kV=12.47 kW=360 pf=1.0",
				"P174_Run_360kW_PV.DSS", 15));
		assertTrue(parser.getGeneratorParser().parseGeneratorPropertyData("generator.g1.duty=PVcurve"));

		Static3PBus bus = parser.getStaticNetwork().getBus("m1026866");
		assertNotNull(bus);
		assertEquals(AclfGenCode.GEN_PQ, bus.getGenCode());
		AclfGen3Phase generator = bus.getPhaseGenList().get(0);
		assertEquals("g1", generator.getId());
		assertEquals(360.0 / baseKva, generator.getPower3Phase(UnitType.PU)
				.a_0.add(generator.getPower3Phase(UnitType.PU).b_1)
				.add(generator.getPower3Phase(UnitType.PU).c_2).getReal(), 1.0e-12);
		assertEquals(0.0, generator.getPower3Phase(UnitType.PU)
				.a_0.add(generator.getPower3Phase(UnitType.PU).b_1)
				.add(generator.getPower3Phase(UnitType.PU).c_2).getImaginary(), 1.0e-12);

		OpenDSSProfileBinding binding = parser.getTimeSeriesData().getGeneratorBinding("G1");
		assertNotNull(binding);
		assertEquals("pvcurve", binding.getShapeId(OpenDSSProfileType.DUTY));

		OpenDSSGeneratorModel model = parser.getTimeSeriesData().getGeneratorModel("g1");
		assertNotNull(model);
		assertEquals("generator", model.getDeviceClass());
		assertEquals(360.0, model.getKw(), 1.0e-12);
		assertEquals(12.47, model.getNominalKV(), 1.0e-12);

		QstsScheduleData scheduleData = parser.getTimeSeriesData().toQstsScheduleData();
		QstsProfileBinding qstsBinding = scheduleData.getProfileBindings().stream()
				.filter(item -> item.getDeviceClass().equals("generator") && item.getDeviceId().equals("g1"))
				.findFirst()
				.orElseThrow();
		assertEquals("pvcurve", qstsBinding.getProfileId("duty"));
	}

	@Test
	void parsedStaticGeneratorUsesOpenDssVoltageLimitFallbackForTerminalPower() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();
		assertTrue(parser.getGeneratorParser().parseGeneratorData(
				"New generator.G1 Bus1=m1026866 kV=12.47 kW=360 pf=1.0 model=1",
				"P174_Run_360kW_PV.DSS", 15));

		AclfGen3Phase parsed = parser.getStaticNetwork().getBus("m1026866").getPhaseGenList().get(0);
		assertTrue(parsed instanceof OpenDSSStaticGenerator);
		OpenDSSStaticGenerator generator = (OpenDSSStaticGenerator) parsed;
		Complex3x1 terminalPower = generator.getTerminalPower3Phase(new Complex3x1(
				new Complex(0.85, 0.0), new Complex(0.89, 0.0), new Complex(1.0, 0.0)),
				UnitType.PU);

		double phasePower = 360.0 / 3.0 / parser.getStaticNetwork().getBaseKva();
		assertEquals(phasePower * Math.pow(0.85 / 0.9, 2.0),
				terminalPower.a_0.getReal(), 1.0e-12);
		assertEquals(phasePower * Math.pow(0.89 / 0.9, 2.0),
				terminalPower.b_1.getReal(), 1.0e-12);
		assertEquals(phasePower, terminalPower.c_2.getReal(), 1.0e-12);
	}

	@Test
	void parsedStaticGeneratorInjectsCurrentOnPhaseBase() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();
		assertTrue(parser.getGeneratorParser().parseGeneratorData(
				"New generator.G1 Bus1=m1026866 kV=12.47 kW=360 pf=1.0 model=1",
				"P174_Run_360kW_PV.DSS", 15));

		AclfGen3Phase parsed = parser.getStaticNetwork().getBus("m1026866").getPhaseGenList().get(0);
		assertTrue(parsed instanceof OpenDSSStaticGenerator);
		OpenDSSStaticGenerator generator = (OpenDSSStaticGenerator) parsed;
		double[] voltage = {
				1.0, 0.0,
				1.0, 0.0,
				0.85, 0.0
		};
		double[] current = new double[6];

		generator.addEquivCurrInj(voltage, 0, current, 0, 0b111);

		double phasePower = 360.0 / 3.0 / parser.getStaticNetwork().getBaseKva();
		assertEquals(phasePower * 3.0, current[0], 1.0e-12);
		assertEquals(phasePower * 3.0, current[2], 1.0e-12);
		assertEquals(phasePower * Math.pow(0.85 / 0.9, 2.0) * 3.0 / 0.85,
				current[4], 1.0e-12);
		assertEquals(0.0, current[1], 1.0e-12);
		assertEquals(0.0, current[3], 1.0e-12);
		assertEquals(0.0, current[5], 1.0e-12);
	}

	@Test
	void readsIeee8500PvGeneratorAndDutyCurveFromCheckedInDssFile() throws Exception {
		Path feederDir = Path.of("testData/feeder/IEEE8500");
		Path dssFile = feederDir.resolve("P174_Run_360kW_PV.DSS");
		List<String> lines = Files.readAllLines(dssFile);
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();

		String generatorLine = firstLineContaining(lines, "new generator.g1");
		String loadShapeLine = firstLineContaining(lines, "new loadshape.pvcurve");
		String dutyBindingLine = firstLineContaining(lines, "generator.g1.duty");

		assertTrue(parser.getGeneratorParser().parseGeneratorData(generatorLine, dssFile.getFileName().toString(), 17));
		assertTrue(parser.getLoadShapeParser().parseLoadShape(loadShapeLine, feederDir.toString(),
				dssFile.getFileName().toString(), 21));
		assertTrue(parser.getGeneratorParser().parseGeneratorPropertyData(dutyBindingLine));

		OpenDSSLoadShape shape = parser.getTimeSeriesData().getShapeRegistry().get("pvcurve");
		assertNotNull(shape);
		assertEquals(2913, shape.getNpts());
		assertEquals(2913, shape.getPointCount());
		assertEquals(1.0 / 3600.0, shape.getIntervalHours(), 1.0e-12);
		assertEquals(0.763235294, shape.getPMult()[0], 1.0e-12);

		QstsScheduleData scheduleData = parser.getTimeSeriesData().toQstsScheduleData();
		QstsProfile profile = scheduleData.getProfileRegistry().get("pvcurve");
		assertNotNull(profile);
		assertEquals(2913, profile.getPointCount());
		assertEquals(0.763235294, profile.getPMultiplierAtIndex(0), 1.0e-12);

		QstsProfileBinding qstsBinding = scheduleData.getProfileBindings().stream()
				.filter(item -> item.getDeviceClass().equals("generator") && item.getDeviceId().equals("g1"))
				.findFirst()
				.orElseThrow();
		assertEquals("pvcurve", qstsBinding.getProfileId("duty"));
	}

	@Test
	void convertActualValuesToPuRebasesParsedGeneratorSchedules() throws Exception {
		Path feederDir = Path.of("testData/feeder/IEEE8500");
		Path dssFile = feederDir.resolve("P174_Run_360kW_PV.DSS");
		List<String> lines = Files.readAllLines(dssFile);
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.getStaticNetwork().setBaseKva(100000.0);

		String generatorLine = firstLineContaining(lines, "new generator.g1");
		String loadShapeLine = firstLineContaining(lines, "new loadshape.pvcurve");
		String dutyBindingLine = firstLineContaining(lines, "generator.g1.duty");

		assertTrue(parser.getGeneratorParser().parseGeneratorData(generatorLine, dssFile.getFileName().toString(), 17));
		assertTrue(parser.getLoadShapeParser().parseLoadShape(loadShapeLine, feederDir.toString(),
				dssFile.getFileName().toString(), 21));
		assertTrue(parser.getGeneratorParser().parseGeneratorPropertyData(dutyBindingLine));
		assertTrue(parser.convertActualValuesToPU(1.0));

		QstsStateApplier applier = new QstsStateApplier(
				parser.getTimeSeriesData().toQstsScheduleData(),
				parser.getTimeSeriesData().getLoadStateStore(),
				parser.getTimeSeriesData().getGeneratorStateStore());
		assertTrue(applier.apply(new QstsStepContext(0, 0, 0.0, QstsMode.DUTY,
				1.0, 1.0, QstsControlMode.STATIC)));

		AclfGen3Phase generator = parser.getStaticNetwork().getBus("m1026866").getPhaseGenList().get(0);
		double firstMultiplier = 0.805882353;
		assertEquals(360.0 * firstMultiplier, totalKw(generator, parser), 1.0e-9);

		assertTrue(applier.apply(new QstsStepContext(1, 1, 1.0, QstsMode.DUTY,
				1.0, 1.0, QstsControlMode.STATIC)));
		QstsProfile profile = parser.getTimeSeriesData().toQstsScheduleData()
				.getProfileRegistry().get("pvcurve");
		double secondHourMultiplier = profile.getPMultiplierAtIndex(1373);
		assertEquals(360.0 * secondHourMultiplier, totalKw(generator, parser), 1.0e-9);
	}

	private static double totalKw(AclfGen3Phase generator, OpenDSSDataParser parser) {
		return generator.getPower3Phase(UnitType.PU).a_0
				.add(generator.getPower3Phase(UnitType.PU).b_1)
				.add(generator.getPower3Phase(UnitType.PU).c_2)
				.getReal() * parser.getStaticNetwork().getBaseKva();
	}

	private static String firstLineContaining(List<String> lines, String fragment) {
		String lowerFragment = fragment.toLowerCase();
		return lines.stream()
				.filter(line -> line.toLowerCase().contains(lowerFragment))
				.findFirst()
				.orElseThrow();
	}
}
