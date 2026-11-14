package com.interpss.dstab.svc;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.Svsmo3t2MssKernelTest;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.svc.Svsmo3t2Model;
import com.interpss.dstab.svc.Svsmo3t2MssBoundary;
import com.interpss.dstab.svc.Svsmo3t2PeerRegistry;

/** Synthetic metadata cost only; no electrical performance claim. */
public class Svsmo3t2OwnershipMetadataTest extends CorePluginTestSetup {
    @Test void measureLargeNetworkPreparationAllocation(@TempDir Path temp) throws Exception {
        Path fixture = Path.of("testData/psse/v33/SVSMO3T2");
        String raw = Files.readString(fixture.resolve("smib.raw")).replace("0 / END OF FIXED SHUNT DATA",
                "3,'S0',0,0,10\n0 / END OF FIXED SHUNT DATA");
        Path input = temp.resolve("network.raw"); Files.writeString(input, raw);
        var context = new PSSEMultiFileLoader().loadDStab(input.toString(), fixture.resolve("smib.dyr").toString());
        var net = context.getDStabilityNet();
        for (int bus = 4; bus <= 7000; bus++) com.interpss.dstab.DStabObjectFactory.createDStabBus("Bus" + bus, net);
        var data = Svsmo3t2MssKernelTest.data(Map.of("mssSwitchingFlag", 1), 1);
        var boundaries = new Svsmo3t2MssBoundary[32];
        for (int i = 0; i < boundaries.length; i++) boundaries[i] = new Svsmo3t2MssBoundary(net, data);
        var bean = ManagementFactory.getThreadMXBean();
        assertInstanceOf(com.sun.management.ThreadMXBean.class, bean);
        var allocation = (com.sun.management.ThreadMXBean) bean;
        assertTrue(allocation.isThreadAllocatedMemorySupported()); allocation.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        for (int pass = -6; pass < 7; pass++) {
            long before = allocation.getThreadAllocatedBytes(thread), start = System.nanoTime();
            for (var boundary : boundaries) boundary.initialize();
            long nanos = System.nanoTime() - start, bytes = allocation.getThreadAllocatedBytes(thread) - before;
            if (pass >= 0) System.out.println("SVS_METADATA sample=" + pass + " buses=7000 boundaries=32 bytes=" + bytes + " nanos=" + nanos);
        }
        for (int pass = 0; pass < 7; pass++) {
            net.eAdapters().removeIf(Svsmo3t2PeerRegistry.class::isInstance);
            long before = allocation.getThreadAllocatedBytes(thread), start = System.nanoTime();
            for (var boundary : boundaries) boundary.initialize();
            long nanos = System.nanoTime() - start, bytes = allocation.getThreadAllocatedBytes(thread) - before;
            System.out.println("SVS_METADATA_REBUILD sample=" + pass + " buses=7000 boundaries=32 bytes=" + bytes + " nanos=" + nanos);
            assertTrue(bytes < 128 * 1024, "One compact network snapshot, not one per boundary");
        }
        for (var boundary : boundaries) assertEquals(0, boundary.controller().completionCount());
    }
    @Test void snapshotsAreSharedOnlyWithinOneOwnerAndNotCopied() {
        var first = com.interpss.dstab.DStabObjectFactory.createDStabilityNetwork();
        com.interpss.dstab.DStabObjectFactory.createDStabBus("Bus1", first);
        var registry = Svsmo3t2PeerRegistry.acquire(first);
        assertSame(registry, Svsmo3t2PeerRegistry.acquire(first));
        var copy = org.eclipse.emf.ecore.util.EcoreUtil.copy(first);
        assertTrue(copy.eAdapters().stream().noneMatch(Svsmo3t2PeerRegistry.class::isInstance));
        var copied = Svsmo3t2PeerRegistry.acquire(copy);
        assertNotSame(registry, copied); copied.preflight(); registry.preflight();
        var second = com.interpss.dstab.DStabObjectFactory.createDStabilityNetwork();
        com.interpss.dstab.DStabObjectFactory.createDStabBus("Bus1", second);
        // Even a wrongly transplanted adapter cannot supply another owner's snapshot.
        second.eAdapters().add(registry);
        assertNotSame(registry, Svsmo3t2PeerRegistry.acquire(second));
    }

    @Test void previouslyEmptyBusAndSameSizeReplacementInvalidateOldSnapshots() throws Exception {
        Path fixture = Path.of("testData/psse/v33/SVSMO3T2");
        var context = new PSSEMultiFileLoader().loadDStab(fixture.resolve("smib.raw").toString(), fixture.resolve("smib.dyr").toString());
        var net = context.getDStabilityNet();
        var empty = com.interpss.dstab.DStabObjectFactory.createDStabBus("Bus4", net).orElseThrow();
        var original = (Svsmo3t2Model) net.getDStabBus("Bus3").getDynamicBusDeviceList().getFirst();
        var first = Svsmo3t2PeerRegistry.acquire(net);
        var added = new Svsmo3t2Model(empty, empty, original.getStaticDevice(), original.getData());
        empty.getDynamicBusDeviceList().add(added);
        assertThrows(IllegalStateException.class, first::preflight);
        var second = Svsmo3t2PeerRegistry.acquire(net);
        assertNotSame(first, second); second.preflight();
        var replacement = new Svsmo3t2Model(empty, empty, original.getStaticDevice(), original.getData());
        empty.getDynamicBusDeviceList().remove(added);
        assertEquals(1, empty.getDynamicBusDeviceList().size());
        assertSame(replacement, empty.getDynamicBusDeviceList().getFirst());
        assertThrows(IllegalStateException.class, second::preflight);
        var third = Svsmo3t2PeerRegistry.acquire(net); third.preflight();
        empty.getDynamicBusDeviceList().clear();
        assertThrows(IllegalStateException.class, third::preflight);
        var fourth = Svsmo3t2PeerRegistry.acquire(net); fourth.preflight();
        // Old owners cannot silently adopt a newer registry when another owner reinitializes.
        assertThrows(IllegalStateException.class, third::preflight);
        net.getBusList().remove(empty);
        assertThrows(IllegalStateException.class, fourth::preflight);
    }

}
