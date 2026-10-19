package org.interpss.core.dstab.reference;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.controller.cml.annotate.AbstractChildAnnotateController;
import com.interpss.dstab.controller.cml.annotate.AbstractAnnotateController;
import com.interpss.dstab.controller.cml.wrapper.BaseFieldAnWrapper;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper;
import com.interpss.dstab.controller.cml.wrapper.ControllerFieldAnWrapper;

/** Checks that audit observations detect a bad rate without changing the state. */
class Texas2kInitializationAuditTest {
    private static void installFixtureField(AbstractAnnotateController controller,BaseFieldAnWrapper<?> field) throws Exception {
        var registry=AbstractAnnotateController.class.getDeclaredField("fieldWrapperList");
        registry.setAccessible(true);registry.set(controller,new java.util.ArrayList<>(java.util.List.of(field)));
        controller.refreshNamedStateBlocks();
    }
    @Test void readsNestedRatesWithoutAdvancingBlocks() throws Exception {
        var parent=new Ieee1992PSS2AStabilizer();
        var child=(AbstractChildAnnotateController)parent.getFieldObject(parent.getField("customBlock1"));
        var block=new DelayControlBlock(1.,.1);
        block.restoreIntegrationState(.4,.2,2.);
        var field=new BlockFieldAnWrapper<DelayControlBlock>("probe");field.setField(block);
        installFixtureField(child,field);
        var childField=new ControllerFieldAnWrapper("child");childField.setField(child);
        installFixtureField(parent,childField);
        var first=Texas2kInitializationAudit.controller(parent);
        assertEquals(.2,((Map<?,?>)first.get("states")).get("child.probe"));
        assertEquals(2.,((Map<?,?>)first.get("rates")).get("child.probe"));
        assertEquals(.4,block.getU(),0.);assertEquals(.2,block.getStateX(),0.);assertEquals(2.,block.getDX_dt(),0.);
        assertEquals(first,Texas2kInitializationAudit.controller(parent));
        block.restoreIntegrationState(.2,.2,0.);
        assertEquals(0.,((Map<?,?>)Texas2kInitializationAudit.controller(parent).get("rates")).get("child.probe"));
    }

    @Test void rejectsNonfiniteRetainedDerivative() throws Exception {
        var controller=new Ieee1992PSS2AStabilizer();
        var block=new DelayControlBlock(1.,.1);block.restoreIntegrationState(.2,.2,Double.NaN);
        var field=new BlockFieldAnWrapper<DelayControlBlock>("probe");field.setField(block);
        installFixtureField(controller,field);
        assertThrows(IllegalStateException.class,()->Texas2kInitializationAudit.controller(controller));
        assertTrue(Double.isNaN(block.getDX_dt()));
    }

    @Test void reportsUninitializedControllerCoverage() {
        var report=Texas2kInitializationAudit.controller(new Ieee1992PSS2AStabilizer());
        assertTrue(((java.util.List<?>)report.get("rate_coverage_gaps")).contains("CML fields not initialized"));
    }

    @Test void inspectsEveryCompoundFilterSection() throws Exception {
        var controller=new Ieee1992PSS2AStabilizer();
        var compound=new com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock(.1,.2,3,2);
        assertTrue(compound.initStateY0(.5));
        var blocks=compound.getIntegrationBlocks();assertEquals(6,blocks.size());
        blocks.get(3).restoreIntegrationState(.4,.2,8.);
        var field=new BlockFieldAnWrapper<com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock>("ramp");
        field.setField(compound);installFixtureField(controller,field);
        var report=Texas2kInitializationAudit.controller(controller);
        assertEquals(6,((Map<?,?>)report.get("rates")).size());
        assertEquals(8.,((Map<?,?>)report.get("rates")).get("ramp.section3"));
        assertEquals(.2,blocks.get(3).getStateX(),0.);
    }
}
