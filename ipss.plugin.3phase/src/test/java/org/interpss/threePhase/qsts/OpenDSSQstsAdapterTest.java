package org.interpss.threePhase.qsts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.qsts.opendss.OpenDSSQstsStudyFactory;
import org.junit.jupiter.api.Test;

import com.interpss.core.threephase.IPhaseGen;
import com.interpss.core.threephase.Static3PBus;

public class OpenDSSQstsAdapterTest {
	@Test
	void factoryUsesStaticNetworkAndKeepsOpenDssMetadataAtAdapterBoundary() {
		OpenDSSDataParser parser = OpenDSSDataParser.forStaticNetwork();
		parser.getStaticNetwork().setBaseKva(100000.0);
		parser.getLoadShapeParser().parseLoadShape("New LoadShape.pvday npts=2 mult=(0.25 0.75)",
				".", "Master.dss", 1);
		parser.getPVSystemParser().parsePVSystemData(
				"New PVSystem.pv1 bus1=bus1.1.2.3 phases=3 kva=100 kw=60 kvar=0 daily=pvday",
				"Master.dss", 2);
		assertEquals(2, parser.getTimeSeriesData().toQstsScheduleData()
				.getProfileRegistry().get("pvday").getPointCount());
		assertEquals("pvday", parser.getTimeSeriesData().toQstsScheduleData().getProfileBindings()
				.get(0).getProfileId("daily"));

		QstsStudy study = OpenDSSQstsStudyFactory.from(parser);
		QstsStepContext context = new QstsStepContext(0, 1, 0.0, QstsMode.DAILY,
				1.0, 1.0, QstsControlMode.OFF);
		study.getStateApplier().apply(context);

		assertFalse(parser.hasDistNetwork());
		Static3PBus bus = parser.getStaticNetwork().getBus("bus1");
		IPhaseGen generator = bus.getPhaseGenList().get(0);
		Complex3x1 power = generator.getPower3Phase(UnitType.PU);
		assertEquals(0.75 * 60.0 / parser.getStaticNetwork().getBaseKva() / 3.0,
				power.a_0.getReal(), 1.0e-12);
		assertEquals(0.0, power.a_0.getImaginary(), 1.0e-12);
		assertEquals(0.75 * 60.0 / parser.getStaticNetwork().getBaseKva(),
				power.a_0.add(power.b_1).add(power.c_2).getReal(), 1.0e-12);
	}
}
