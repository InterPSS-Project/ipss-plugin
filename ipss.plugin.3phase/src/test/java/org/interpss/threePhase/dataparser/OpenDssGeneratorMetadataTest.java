package org.interpss.threePhase.dataparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSGeneratorModel;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSLoadShape;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileBinding;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileType;
import org.interpss.threePhase.qsts.QstsProfileBinding;
import org.interpss.threePhase.qsts.QstsProfile;
import org.interpss.threePhase.qsts.QstsScheduleData;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.threephase.IPhaseGen;
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
		IPhaseGen generator = bus.getPhaseGenList().get(0);
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
		assertEquals(3000, shape.getNpts());
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

	private static String firstLineContaining(List<String> lines, String fragment) {
		String lowerFragment = fragment.toLowerCase();
		return lines.stream()
				.filter(line -> line.toLowerCase().contains(lowerFragment))
				.findFirst()
				.orElseThrow();
	}
}
