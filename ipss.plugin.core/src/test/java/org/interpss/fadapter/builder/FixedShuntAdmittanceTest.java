package org.interpss.fadapter.builder;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.ShuntCompensatorType;
import org.junit.jupiter.api.Test;

class FixedShuntAdmittanceTest {
    @Test void originalAdmittanceIsDeviceOwnedAndIndependentOfStatus() {
        var a=CoreObjectFactory.createShuntCompensator("A",ShuntCompensatorType.CAPACITOR);
        var b=CoreObjectFactory.createShuntCompensator("A",ShuntCompensatorType.CAPACITOR);
        a.setB(.02);b.setB(.02);a.setSteps(1);b.setSteps(1);a.setStatus(false);
        var unrelated=new AdapterImpl();a.eAdapters().add(unrelated);
        FixedShuntAdmittance.retain(a,.003,.02);FixedShuntAdmittance.retain(b,-.007,.02);
        assertEquals(new Complex(.003,.02),FixedShuntAdmittance.find(a).orElseThrow());
        assertEquals(new Complex(-.007,.02),FixedShuntAdmittance.find(b).orElseThrow());
        a.setStatus(true);assertEquals(.003,FixedShuntAdmittance.find(a).orElseThrow().getReal());
        assertTrue(a.eAdapters().contains(unrelated));
        assertTrue(FixedShuntAdmittance.find(EcoreUtil.copy(a)).isEmpty(),"Copies require explicit provenance");
    }
    @Test void missingModifiedOrAmbiguousMetadataFailsClosed() {
        var a=CoreObjectFactory.createShuntCompensator("A",ShuntCompensatorType.CAPACITOR);a.setSteps(1);a.setB(.02);
        assertTrue(FixedShuntAdmittance.find(a).isEmpty());assertTrue(FixedShuntAdmittance.find(null).isEmpty());
        FixedShuntAdmittance.retain(a,.003,.02);a.setB(.03);assertTrue(FixedShuntAdmittance.find(a).isEmpty());
        a.setB(.02);a.setSteps(2);assertTrue(FixedShuntAdmittance.find(a).isEmpty());
        a.setSteps(1);FixedShuntAdmittance.retain(a,.003,.02);assertTrue(FixedShuntAdmittance.find(a).isEmpty());
    }
    @Test void nonfiniteAdmittanceIsRejectedBeforeRetention() {
        var a=CoreObjectFactory.createShuntCompensator("A",ShuntCompensatorType.CAPACITOR);
        assertThrows(IllegalArgumentException.class,()->FixedShuntAdmittance.retain(a,Double.NaN,.01));
        assertThrows(IllegalArgumentException.class,()->FixedShuntAdmittance.retain(a,.01,Double.POSITIVE_INFINITY));
        assertTrue(a.eAdapters().isEmpty());
    }
}
