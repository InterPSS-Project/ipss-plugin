package org.interpss.dstab.control.gov.psse.hyg3;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;

/** Prepared point arrays retain live writes, conventions and custom-provider dispatch. */
public class Hyg3PreparedCurveTest {
    private static volatile double sink;
    private static PsseHyg3Governor governor() { return new PsseHyg3Governor("test", "HYG3", "governor"); }

    @Test void reuseBothDirectionsAndRebuildOnceAfterScalarOrArrayWrites() {
        var model = governor(); var data = model.getData();
        assertEquals(.2, model.gateCurve(.25), 1e-15);
        assertEquals(.25, model.inverseGateCurve(.2), 1e-15);
        long initial = model.getGateCurvePreparationCount(); assertEquals(1, initial);
        for (int i = 0; i < 1000; i++) {
            assertEquals(.75, model.gateCurve(.7), 1e-15);
            assertEquals(.7, model.inverseGateCurve(.75), 1e-15);
        }
        assertEquals(initial, model.getGateCurvePreparationCount());
        data.setGv(2, .55);
        assertEquals(.5, model.gateCurve(.55), 1e-15);
        assertEquals(.55, model.inverseGateCurve(.5), 1e-15);
        assertEquals(initial + 1, model.getGateCurvePreparationCount());
        data.setGv(new double[]{.1, .25, .5, .7, .85, 1.});
        data.setPgv(new double[]{0., .25, .5, .75, .9, 1.});
        assertEquals(.25, model.gateCurve(.25), 1e-15);
        assertEquals(initial + 2, model.getGateCurvePreparationCount());
        data.setPgv(1, .3);
        assertEquals(.3, model.gateCurve(.25), 1e-15);
        assertEquals(initial + 3, model.getGateCurvePreparationCount());
        data.getGv()[1] = .9; data.getPgv()[1] = .9;
        assertEquals(.3, model.gateCurve(.25), 1e-15, "Public point arrays remain defensive copies");
        assertEquals(initial + 3, model.getGateCurvePreparationCount());
    }

    @Test void builtinAndImplicitEndpointsRetainLiveScalingAndRejectInvalidUpdates() {
        var model = governor(); var data = model.getData();
        assertEquals(1.1, model.gateCurve(1.15), 1e-14, "Typed extrapolation");
        long prepared = model.getGateCurvePreparationCount();
        data.setFrequencyDeadbandConvention(PsseHyg3GovernorData.FrequencyDeadbandConvention.PER_UNIT_SCALED_ERROR);
        data.setPmax(2.);
        assertEquals(1.5, model.gateCurve(1.4), 1e-14);
        assertEquals(1.4, model.inverseGateCurve(1.5), 1e-14);
        assertEquals(prepared, model.getGateCurvePreparationCount(), "Base scaling does not rebuild normalized points");
        data.setGv(0, -1.);
        assertEquals(1.6, model.gateCurve(1.34), 1e-14);
        data.setFrequencyDeadbandConvention(PsseHyg3GovernorData.FrequencyDeadbandConvention.HZ_HYSTERESIS);
        data.setGv(new double[]{.1, .4, .8, 0., 0., 0.});
        data.setPgv(new double[]{0., .3, .9, 0., 0., 0.});
        assertEquals(.6, model.gateCurve(.6), 1e-14);
        assertEquals(.6, model.inverseGateCurve(.6), 1e-14);
        long valid = model.getGateCurvePreparationCount();
        data.setGv(2, .3);
        assertThrows(IllegalArgumentException.class, () -> model.gateCurve(.6));
        assertThrows(IllegalArgumentException.class, () -> model.inverseGateCurve(.6));
        assertEquals(valid, model.getGateCurvePreparationCount(), "Failed preparation cannot publish a partial descriptor");
        data.setGv(2, .8); assertEquals(.6, model.gateCurve(.6), 1e-14);
        assertEquals(valid + 1, model.getGateCurvePreparationCount());
    }

    @Test void customPointProviderRetainsObservableGetterDispatch() {
        class CustomData extends PsseHyg3GovernorData {
            int reads; double extra;
            @Override public double[] getPgv() { reads++; var points = super.getPgv(); points[1] += extra; return points; }
        }
        class CustomGovernor extends PsseHyg3Governor {
            CustomGovernor(CustomData data) { super("custom", "HYG3", "governor"); _data = data; }
        }
        var data = new CustomData(); var model = new CustomGovernor(data);
        assertEquals(.2, model.gateCurve(.25), 1e-15); int before = data.reads;
        data.extra = .1;
        assertEquals(.3, model.gateCurve(.25), 1e-15);
        assertTrue(data.reads > before);
        assertEquals(0, model.getGateCurvePreparationCount());
    }

    @Test void equalRevisionsOnDifferentDataOwnersCannotReuseOldPoints() throws Exception {
        var model = governor(); var original = model.getData(); original.setGv(1, .26);
        assertEquals(.2, model.gateCurve(.26), 1e-15);
        var replacement = new PsseHyg3GovernorData(); replacement.setPgv(1, .3);
        assertEquals(original.getGateCurveRevision(), replacement.getGateCurveRevision());
        replaceData(model, replacement);
        assertEquals(.3, model.gateCurve(.25), 1e-15);
        assertEquals(2, model.getGateCurvePreparationCount());
        class UnversionedData extends PsseHyg3GovernorData {
            double extra;
            @Override public double[] getPgv() { var points = super.getPgv(); points[1] += extra; return points; }
        }
        var custom = new UnversionedData(); replaceData(model, custom);
        assertEquals(.2, model.gateCurve(.25), 1e-15);
        custom.extra = .1; assertEquals(.3, model.gateCurve(.25), 1e-15);
        assertEquals(2, model.getGateCurvePreparationCount(), "Custom point data keeps generic getter dispatch even on a standard owner");
    }

    private static void replaceData(PsseHyg3Governor model, PsseHyg3GovernorData data) throws Exception {
        for (Class<?> type = model.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField("_data"); field.setAccessible(true); field.set(model, data); return;
            } catch (NoSuchFieldException inherited) { }
        }
        fail("Controller data field was not found");
    }

    @Test void warmedStandardCurveEvaluationDoesNotAllocatePointArrays() {
        var bean = java.lang.management.ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var allocations = (com.sun.management.ThreadMXBean) bean;
        assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);
        var model = governor(); double sum = 0.;
        for (int i = 0; i < 50000; i++) sum += model.gateCurve((i % 1000) / 1000.);
        long thread = Thread.currentThread().threadId(), before = allocations.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 250000; i++) {
            double x = (i % 1000) / 1000.; sum += model.gateCurve(x) + model.inverseGateCurve(x);
        }
        long bytes = allocations.getThreadAllocatedBytes(thread) - before; sink = sum;
        System.out.println("HYG3 prepared forward/inverse curve evaluations allocated " + bytes + " bytes after warmup");
        assertTrue(Double.isFinite(sink)); assertEquals(1, model.getGateCurvePreparationCount());
        assertTrue(bytes <= 1024, "Prepared curve evaluation allocated " + bytes + " bytes");
    }
}
