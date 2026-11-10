package org.interpss.threePhase.dataparser;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticDataParser;
import org.interpss.threePhase.qsts.QstsResult;
import org.interpss.threePhase.qsts.opendss.OpenDSSQstsStudyFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.interpss.core.threephase.IBranch3Phase;

class OpenDSSInlineMatrixRegressionTest {
    private static final Path FIXTURES = Path.of("src/test/resources/opendss-regression");
    @TempDir Path temporary;

    @BeforeAll static void init() { IpssCorePlugin.init(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void inlineMatricesScaleLengthAndMapConductorsForBothNetworkTypes(boolean staticNetwork) throws Exception {
        OpenDSSDataParser parser = staticNetwork ? OpenDSSDataParser.forStaticNetwork() : new OpenDSSDataParser();
        parser.getLineParser().parseLineData("new line.one phases=1 bus1=a.3 bus2=b.3 length=2 units=km "
                + "rmatrix = [.1] xmatrix = (.2) cmatrix = [4]");
        parser.getLineParser().parseLineData("new line.two phases=2 bus1=b.3.1 bus2=c.3.1 length=100 units=m "
                + "rmatrix=[.1 | .02 .3] xmatrix=(.2 | .04 .6) cmatrix=[4 | -1 5]");
        parser.getLineParser().parseLineData("new line.three phases=3 bus1=c bus2=d length=1 units=ft "
                + "rmatrix=[1 .1 .2 | .1 2 .3 | .2 .3 3] "
                + "xmatrix=[2 | .2 4 | .4 .6 6] cmatrix=[4 | -1 5 | -2 -3 6]");
        var branches = staticNetwork ? parser.getStaticNetwork().getBranchList() : parser.getDistNetwork().getBranchList();
        IBranch3Phase one = (IBranch3Phase) branches.get(0);
        assertEquals(new Complex(.2, .4), one.getZabc().cc);
        assertEquals(0.0, one.getZabc().aa.abs());
        assertEquals(2 * Math.PI * 60 * 4e-9, one.getFromShuntYabc().cc.getImaginary(), 1e-18);
        IBranch3Phase two = (IBranch3Phase) branches.get(1);
        assertEquals(new Complex(10, 20), two.getZabc().cc);
        assertEquals(new Complex(30, 60), two.getZabc().aa);
        assertEquals(new Complex(2, 4), two.getZabc().ca);
        assertEquals(0.0, two.getZabc().bb.abs());
        assertEquals(2 * Math.PI * 60 * -1e-9 * 100 / 2,
                two.getToShuntYabc().ac.getImaginary(), 1e-18);
        IBranch3Phase three = (IBranch3Phase) branches.get(2);
        assertEquals(new Complex(.2, .4), three.getZabc().ac);
        assertEquals(new Complex(3, 6), three.getZabc().cc);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "rmatrix=[.1]", "cmatrix=[4]", "rmatrix=[1 2] xmatrix=[.2]",
        "rmatrix=[NaN] xmatrix=[.2]", "rmatrix=[.1 xmatrix=[.2]",
        "rmatrix=[.1] xmatrix=[.2] linecode=unused",
        "rmatrix=[.1] xmatrix=[.2] r1=.3", "rmatrix=[.1] xmatrix=[.2] rmatrix=[.3]"
    })
    void unsupportedOrMalformedInlineMatricesFailExplicitly(String parameters) {
        var parser = OpenDSSDataParser.forStaticNetwork();
        assertThrows(IllegalArgumentException.class, () -> parser.getLineParser().parseLineData(
                "new line.bad phases=1 bus1=a.1 bus2=b.1 length=1 " + parameters));
        assertTrue(parser.getStaticNetwork().getBranchList().isEmpty());
    }

    @Test
    void inlineAndNamedMatricesHaveEqualPerUnitImpedanceShuntsAndSolvedLosses() throws Exception {
        String inline = Files.readString(FIXTURES.resolve("inline-single-phase.dss"));
        String named = inline.replace("new line.feeder", "new linecode.code nphases=1 units=km "
                + "rmatrix=[.1] xmatrix=[.05] cmatrix=[0]\nnew line.feeder")
                .replace("~ rmatrix=[.1] xmatrix=[.05] cmatrix=[0]", "~ linecode=code");
        Path namedFile = temporary.resolve("named.dss");
        Files.writeString(namedFile, named);
        OpenDSSStaticDataParser inlineParser = parse(FIXTURES.resolve("inline-single-phase.dss"));
        OpenDSSStaticDataParser namedParser = parse(namedFile);
        IBranch3Phase a = line(inlineParser), b = line(namedParser);
        double zbase = Math.pow(12470, 2) / 1e6;
        assertEquals(.1 / zbase, a.getZabc().aa.getReal(), 1e-15);
        assertEquals(.05 / zbase, a.getZabc().aa.getImaginary(), 1e-15);
        assertEquals(b.getZabc().aa, a.getZabc().aa);
        double inlineLoss = loss(run(inlineParser)), namedLoss = loss(run(namedParser));
        assertEquals(namedLoss, inlineLoss, 1e-10);
        assertEquals(.000192928725, inlineLoss, 1e-8);

        // Equivalent physical data in metres must give the same total ohms and loss.
        Path metresFile = temporary.resolve("metres.dss");
        Files.writeString(metresFile, inline.replace("length=1 units=km", "length=1000 units=m")
                .replace("rmatrix=[.1] xmatrix=[.05]", "rmatrix=[.0001] xmatrix=[.00005]"));
        var metresParser = parse(metresFile);
        assertEquals(a.getZabc().aa.getReal(), line(metresParser).getZabc().aa.getReal(), 1e-15);
        assertEquals(inlineLoss, loss(run(metresParser)), 1e-10);
    }

    @Test
    void inlineMatricesUseTheSameSmallImpedanceFloorAsNamedMatrices() throws Exception {
        var parser = OpenDSSDataParser.forStaticNetwork();
        parser.getLineParser().parseLineData("new line.tiny phases=1 bus1=a.1 bus2=b.1 length=1 "
                + "rmatrix=[.00001] xmatrix=[.00002]");
        assertEquals(new Complex(.00001, .00002), parser.getStaticNetwork().getBranchList().get(0).getZabc().aa);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1 2 | 3]", "[1 | 2 | 3]", "[1 2 | 3 4]"})
    void malformedOrAsymmetricRowsFailExplicitly(String matrix) {
        var parser = OpenDSSDataParser.forStaticNetwork();
        assertThrows(IllegalArgumentException.class, () -> parser.getLineParser().parseLineData(
                "new line.bad phases=2 bus1=a.1.2 bus2=b.1.2 rmatrix=" + matrix + " xmatrix=[1 | 0 1]"));
    }

    @Test
    void inlineCapacitanceUsesLengthAndFeederAdmittanceBase() throws Exception {
        String fixture = Files.readString(FIXTURES.resolve("inline-single-phase.dss"))
                .replace("length=1 units=km", "length=2 units=km").replace("cmatrix=[0]", "cmatrix=[4]");
        Path file = temporary.resolve("capacitance.dss");
        Files.writeString(file, fixture);
        var parser = parse(file);
        double zbase = Math.pow(12470, 2) / 1e6;
        assertEquals(.2 / zbase, line(parser).getZabc().aa.getReal(), 1e-15);
        assertEquals(2 * Math.PI * 60 * 4e-9 * zbase,
                line(parser).getFromShuntYabc().aa.getImaginary(), 1e-15);
    }

    private static IBranch3Phase line(OpenDSSStaticDataParser parser) {
        return parser.getStaticNetwork().getBranchList().stream()
                .filter(branch -> "feeder".equals(branch.getName())).findFirst().orElseThrow();
    }

    private static OpenDSSStaticDataParser parse(Path file) {
        var parser = OpenDSSDataParser.forStaticNetwork();
        parser.setPreserveSpecifiedLineImpedance(true);
        assertTrue(parser.parseFeederData(file.getParent().toString(), file.getFileName().toString()));
        assertTrue(parser.calcVoltageBases());
        assertTrue(parser.convertActualValuesToPU(1));
        return parser;
    }

    private static QstsResult run(OpenDSSStaticDataParser parser) {
        var result = OpenDSSQstsStudyFactory.from(parser).setNumberOfSteps(1).setTolerance(1e-10).run();
        assertTrue(result.isConverged());
        return result;
    }

    private static double loss(QstsResult result) {
        return result.getStep(0).getBranchPowers().stream()
                .filter(sample -> "feeder".equals(sample.getElementId()))
                .mapToDouble(sample -> sample.getActivePowerKw()).sum();
    }
}
