package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.interpss.dstab.validation.DynamicParameterValidator;
import org.interpss.dstab.dynLoad.impl.Cmldblu1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.IpssCorePlugin;
import org.apache.commons.math3.complex.Complex;

/** Oversubscribed legacy motor protection is reported without changing model data. */
public class CompositeMotorOverlapValidationTest {
    @ParameterizedTest @ValueSource(doubles={.69,.70,.71})
    void overlapWarningIsNonmutating(double second)throws Exception {
        IpssCorePlugin.init();
        var context=new PSSEMultiFileLoader().loadDStab(Cmldblu1SharedSmibTest.CASE.resolve("load-network.raw").toString(),
                Cmldblu1SharedSmibTest.CASE.resolve("cmldblu1-mixed.dyr").toString());
        var original=(Cmldblu1Model)context.getDStabilityNet().getDStabBus("Bus2").getInfoOnlyDynModel();
        double[] constants=original.getLegacyConstants();
        for(int m=0;m<3;m++){constants[49+20*m]=.3;constants[54+20*m]=second;}
        var network=com.interpss.dstab.DStabObjectFactory.createDStabilityNetwork();
        var bus=(com.interpss.dstab.DStabBus)com.interpss.dstab.DStabObjectFactory.createDStabBus("B",network).get();
        var model=new Cmldblu1Model("C",bus,java.util.Set.of("1"),new Complex(.4,.1),constants);
        double[] before=model.getLegacyConstants();
        var report=DynamicParameterValidator.validate(network,.0005,1,m->1);
        var warnings=report.issues().stream().filter(i->i.model().equals("CMLDBLU1")).toList();
        assertEquals(second>.7?3:0,warnings.size());
        for(var warning:warnings){assertEquals(DynamicParameterValidator.Severity.WARNING,warning.severity());assertEquals(.3+second,warning.value(),0);}
        assertArrayEquals(before,model.getLegacyConstants());assertArrayEquals(before,constants);
        report.requireNoErrors();
        assertEquals(1,report.checkedModels().get("CMLDBLU1.UvOverlap"));
        assertEquals(1,report.uncoveredModels().get("CMLDBLU1.otherParameters"));
    }
}
