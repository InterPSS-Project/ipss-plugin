package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.interpss.dstab.mach.Gentpj1Data;

/** Type-J native values must not pass through a different model's correction rules. */
class Gentpj1ParameterPreservationTest {
    private Gentpj1Data source() {
        // Synthetic removed-q-transient profile. The legacy compound view
        // would reduce Xq'' to a quarter of Xq and then lower Xl as well.
        return new Gentpj1Data(5.,.04,0.,.03,3.,.1,1.8,.9,.6,.9,.3,.3,.24,.1,.3,0.);
    }
    @Test void nativeDataRetainsValidRemovedWindingReactances() {
        var d=source();assertEquals(.3,d.xqpp(),0);assertEquals(.24,d.xl(),0);
        assertEquals(0,d.tqop(),0);assertEquals(.9,d.xqp(),0);assertEquals(0,d.kis(),0);
    }
    @Test void builderWiresNativeTypeJValuesRatherThanCompatibilityView() throws Exception {
        var builder=DStabBuilderTestFixture.createBuilder();var d=source();
        var m=builder.addGentpj1("Bus1","1",100.,16.5,d);
        assertEquals(d.xqpp(),m.getXq11(),0);assertEquals(d.xl(),m.getXl(),0);
        assertEquals(d.xdpp(),m.getXd11(),0);assertEquals(d.xqp(),m.getXq1(),0);
        assertEquals(d.tqop(),m.getTq01(),0);assertEquals(d.tqopp(),m.getTq011(),0);
        assertTrue(m.checkData(null));
        var report=org.interpss.dstab.validation.DynamicParameterValidator.validate(
                builder.getDStabNetwork(),1./240,4.,machine->1);
        assertFalse(report.issues().stream().anyMatch(i->i.parameter().equals("Tq0'")
                &&i.severity()==org.interpss.dstab.validation.DynamicParameterValidator.Severity.ERROR));
        assertEquals(d.xqpp(),m.getXq11(),0);assertEquals(d.xl(),m.getXl(),0);
    }
    @Test void nonsingularNativeQOrderingIsRetainedAndReported() throws Exception {
        var d=new Gentpj1Data(5.,.04,.5,.03,3.,.1,1.8,1.,.6,.2,.3,.25,.1,.1,.3,.1);
        var builder=DStabBuilderTestFixture.createBuilder();
        var m=builder.addGentpj1("Bus1","1",100.,16.5,d);
        assertEquals(.25,m.getXq11(),0);assertEquals(.2,m.getXq1(),0);
        var report=org.interpss.dstab.validation.DynamicParameterValidator.validate(
                builder.getDStabNetwork(),1./240,4.,machine->1);
        var issue=report.issues().stream().filter(i->i.parameter().equals("Xq'' <= Xq'")).findFirst().orElseThrow();
        assertEquals(org.interpss.dstab.validation.DynamicParameterValidator.Severity.WARNING,issue.severity());
        assertEquals(.25,m.getXq11(),0);assertEquals(.1,m.getXl(),0);
    }
    @Test void unsupportedOrNonfiniteNativeValuesAreRejectedWithoutCorrection() {
        assertThrows(IllegalArgumentException.class,()->new Gentpj1Data(5.,.04,0.,.03,3.,.1,
                1.8,.9,.6,.9,.6,.3,.24,.1,.3,0.));
        assertThrows(IllegalArgumentException.class,()->new Gentpj1Data(5.,.04,0.,.03,3.,.1,
                1.8,.9,.6,.9,.3,.3,Double.NaN,.1,.3,0.));
    }
}
