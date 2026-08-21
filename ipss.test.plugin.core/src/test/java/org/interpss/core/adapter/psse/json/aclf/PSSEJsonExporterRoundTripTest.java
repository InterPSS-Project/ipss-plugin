package org.interpss.core.adapter.psse.json.aclf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dep.QA.compare.aclf.AclfBranchDataComparator;
import org.interpss.dep.QA.compare.aclf.AclfBusDataComparator;
import org.interpss.dep.QA.compare.aclf.AclfNetDataComparator;
import org.interpss.dep.QA.compare.aclf.AclfNetModelComparator;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.interpss.fadapter.psse.export.PSSEJsonExporter;
import org.interpss.fadapter.psse.export.PSSERawExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.interpss.core.aclf.AclfNetwork;

public class PSSEJsonExporterRoundTripTest extends CorePluginTestSetup {

	private static final double TOL = 1.0E-8;

	@TempDir
	Path tempDir;

	@Test
	public void ieee9RawxExportsAndParsesToEquivalentNetwork() throws Exception {
		AclfNetwork original = new PSSEJsonDirectParser()
				.parse("testData/adpter/psse/json/ieee9.rawx");

		Path exported = tempDir.resolve("ieee9-roundtrip.rawx");
		new PSSEJsonExporter(original).export(exported);

		AclfNetwork roundTrip = new PSSEJsonDirectParser().parse(exported.toString());

		AclfNetModelComparator comparator = new AclfNetModelComparator(
				new AclfNetDataComparator(TOL),
				new AclfBusDataComparator(TOL),
				new AclfBranchDataComparator(TOL));
		assertTrue(comparator.compare(original, roundTrip), comparator::getMsg);
	}

	@ParameterizedTest
	@CsvSource({
			"34,testData/psse/v34/sample_v34.raw",
			"35,testData/psse/v35/sample_v35.raw",
			"36,testData/psse/v36/sample_v36.raw"
	})
	public void rawExportsAndParsesToEquivalentNetworkByVersion(
			int version, String rawFile) throws Exception {
		AclfNetwork original = new PSSEDirectParser(version).parse(rawFile);

		Path exported = tempDir.resolve("sample_v" + version + "-roundtrip.raw");
		new PSSERawExporter(original, version).export(exported);

		AclfNetwork roundTrip = new PSSEDirectParser(version).parse(exported.toString());

		AclfNetModelComparator comparator = new AclfNetModelComparator(
				new AclfNetDataComparator(TOL),
				new AclfBusDataComparator(TOL),
				new AclfBranchDataComparator(TOL));
		assertTrue(comparator.compare(original, roundTrip),
				() -> rawFile + " -> " + exported + comparator.getMsg());
	}

	@Test
	public void rawV35ExportsAsV36AndParsesToEquivalentNetwork() throws Exception {
		AclfNetwork original = new PSSEDirectParser(35)
				.parse("testData/psse/v35/sample_v35.raw");

		Path exported = tempDir.resolve("sample_v35-to-v36.raw");
		new PSSERawExporter(original, 36).export(exported);

		AclfNetwork roundTrip = new PSSEDirectParser(36).parse(exported.toString());

		AclfNetModelComparator comparator = new AclfNetModelComparator(
				new AclfNetDataComparator(TOL),
				new AclfBusDataComparator(TOL),
				new AclfBranchDataComparator(TOL));
		assertTrue(comparator.compare(original, roundTrip),
				() -> "testData/psse/v35/sample_v35.raw -> " + exported + comparator.getMsg());
	}

}
