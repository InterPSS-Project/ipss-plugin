package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.svc.Svsmo3t2Data;
import org.interpss.dstab.svc.Svsmo3t2MssController;
import org.interpss.dstab.svc.Svsmo3t2Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;

/** Accepted-endpoint switching histories, independent of continuous integration. */
public class Svsmo3t2MssKernelTest extends CorePluginTestSetup {
    public static Svsmo3t2Data data(Map<String, Object> overrides, int slots) throws Exception {
        var root = Path.of("testData/psse/v33/SVSMO3T2");
        var context = new PSSEMultiFileLoader().loadDStab(root.resolve("smib.raw").toString(),
                root.resolve("smib.dyr").toString());
        var model = (Svsmo3t2Model) context.getDStabilityNet().getDStabBus("Bus3")
                .getDynamicBusDeviceList().stream().filter(Svsmo3t2Model.class::isInstance).findFirst().orElseThrow();
        var devices = new ArrayList<Svsmo3t2Data.MssDevice>();
        for (int i = 0; i < 8; i++) devices.add(new Svsmo3t2Data.MssDevice(i < slots ? 3 : 0, "S" + i));
        var fields = Svsmo3t2Data.class.getRecordComponents();
        var arguments = new Object[fields.length];
        var types = new Class<?>[fields.length];
        for (int i = 0; i < fields.length; i++) {
            types[i] = fields[i].getType();
            Object original = fields[i].getName().equals("mssDevices") ? devices : fields[i].getAccessor().invoke(model.getData());
            arguments[i] = overrides.getOrDefault(fields[i].getName(), original);
        }
        for (String key : overrides.keySet()) assertTrue(java.util.Arrays.stream(fields).anyMatch(f -> f.getName().equals(key)), key);
        return Svsmo3t2Data.class.getConstructor(types).newInstance(arguments);
    }

    private static Svsmo3t2MssController controller(double[] b, boolean[] active) throws Exception {
        var data = data(Map.of("mssSwitchingFlag", 1, "mssBreakerTime", .015625,
                "capacitorDischargeTime", .25, "mssSwitchingTime", .03125,
                "mssUpperCurrent", .5, "mssLowerCurrent", -.5), b.length);
        var result = new Svsmo3t2MssController(data);
        result.initialize(java.util.Arrays.copyOf(b, 8), java.util.Arrays.copyOf(active, 8));
        return result;
    }

    @Test void equalityIsInBandAndIssuedBreakerIsLatched() throws Exception {
        var c = controller(new double[]{.1}, new boolean[]{false});
        assertEquals(-1, c.advance(0, .5));
        assertEquals(-1, c.upperDemandStart());
        c.advance(.125, 1);
        assertEquals(.125, c.upperDemandStart());
        c.advance(.15625, 1);
        assertEquals(1, c.pendingDirection());
        assertEquals(.15625, c.breakerStart());
        c.advance(.1640625, 0);
        assertEquals(-1, c.upperDemandStart());
        assertEquals(1, c.pendingDirection(), "An issued breaker is independent of subsequent demand");
        assertEquals(0, c.advance(.171875, 0));
        assertTrue(c.isInService(0));
        assertEquals(1, c.completionCount());
        assertEquals(-1, c.advance(.171875, -1), "Duplicate event-side calls cannot start a second operation");
        assertEquals(-1, c.lowerDemandStart());
        assertEquals(0, c.pendingDirection());
    }

    @Test void orderedSlotsOverrideRemovalPreferenceAndOnlyOneBankOperates() throws Exception {
        var c = controller(new double[]{.1, -.1}, new boolean[]{false, true});
        c.advance(0, 1);
        assertEquals(0, c.advance(.046875, 1));
        assertTrue(c.isInService(0));
        assertTrue(c.isInService(1), "An earlier capacitor insertion wins over a later reactor removal");
        assertEquals(-1, c.advance(.05, 1));
        assertEquals(1, c.advance(.10, 1));
        assertFalse(c.isInService(1));
        assertEquals(2, c.completionCount());
    }

    @Test void coolingCapacitorRetriesAfterFreshDemandAndCanBeSkipped() throws Exception {
        var c = controller(new double[]{.1, .15}, new boolean[]{true, false});
        c.advance(0, -1);
        assertEquals(0, c.advance(.046875, -1));
        assertEquals(.046875, c.openedAt(0));
        c.advance(.0625, 1);
        assertEquals(1, c.advance(.109375, 1), "A ready later bank may operate while the first capacitor discharges");
        c.advance(.125, 1);
        assertEquals(-1, c.advance(.171875, 1));
        assertEquals(3, c.attemptCount());
        assertEquals(2, c.completionCount());
        assertEquals(-1, c.upperDemandStart(), "A failed operation resets its demand delay");
        c.advance(.28125, 1);
        assertEquals(-1, c.advance(.296875, 1), "Discharge expiry alone cannot bypass the renewed switching delay");
        assertEquals(0, c.advance(.328125, 1));
        assertTrue(c.isInService(0));
    }

    @Test void reactorHasNoCapacitorDischargeDelayAndDemandReversalResetsPickup() throws Exception {
        var c = controller(new double[]{-.1}, new boolean[]{true});
        c.advance(0, 1);
        c.advance(.015625, -.5);
        assertEquals(-1, c.upperDemandStart());
        c.advance(.03125, 1);
        assertEquals(0, c.advance(.078125, 1));
        c.advance(.09375, -1);
        assertEquals(0, c.advance(.140625, -1));
        assertTrue(c.isInService(0));
        assertThrows(IllegalArgumentException.class, () -> c.advance(.1, 0));
        assertThrows(IllegalArgumentException.class, () -> c.advance(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> c.advance(.2, Double.POSITIVE_INFINITY));
        c.initialize(new double[]{-.1,0,0,0,0,0,0,0}, new boolean[8]);
        assertEquals(0, c.time());
        assertEquals(0, c.attemptCount());
        assertFalse(c.isInService(0));
    }

    @Test void warmedEndpointEvaluationReusesNumericalStorage() throws Exception {
        var c = controller(new double[]{.1}, new boolean[]{false});
        var bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean allocation && allocation.isThreadAllocatedMemorySupported()) {
            allocation.setThreadAllocatedMemoryEnabled(true);
            for (int i = 0; i < 100000; i++) c.advance(i * .001, i % 1000 < 500 ? 1 : -1);
            long thread = Thread.currentThread().threadId();
            long before = allocation.getThreadAllocatedBytes(thread);
            for (int i = 100000; i < 200000; i++) c.advance(i * .001, i % 1000 < 500 ? 1 : -1);
            long bytes = allocation.getThreadAllocatedBytes(thread) - before;
            System.out.println("SVSMO3T2 MSS warmed endpoint allocation bytes=" + bytes);
            assertTrue(bytes < 4096, "No per-endpoint slot, timer or action allocations");
            assertTrue(c.completionCount() > 100);
        }
    }

    @Test void reactorInsertionRetainsLowerPickupForTheNextOrderedReactor() throws Exception {
        var c = controller(new double[]{-.1, -.15}, new boolean[]{false, false});
        c.advance(0, -1);
        assertEquals(0, c.advance(.046875, -1));
        assertEquals(0, c.lowerDemandStart());
        assertEquals(-1, c.advance(.046875, -1), "Still one operation per accepted endpoint");
        assertEquals(1, c.advance(.0625, -1), "Independent source insertion retains the satisfied delay");
        assertEquals(2, c.completionCount());
        c.advance(.078125, -1);
        assertEquals(-1, c.lowerDemandStart(), "No remaining reactor clears the retained pickup");
    }

    @Test void enabledSelectorWithoutBanksRejectsAnIgnoredSourceModel() {
        var error = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> data(Map.of("mssSwitchingFlag", 1), 0));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("entire source model would be ignored"));
    }
}
