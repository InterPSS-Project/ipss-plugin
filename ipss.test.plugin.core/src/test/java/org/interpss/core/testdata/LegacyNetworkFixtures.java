package org.interpss.core.testdata;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.ieeecdf.IeeeCDFDirectParser;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.ChildNetObjectFactory;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.BranchBusSide;
import com.interpss.core.net.childnet.ChildNetInterfaceBranch;
import com.interpss.core.net.childnet.ChildNetInterfaceType;
import com.interpss.core.net.childnet.ChildNetworkWrapper;
import com.interpss.dist.DistBranch;
import com.interpss.dist.DistBus;
import com.interpss.dist.DistNetwork;
import com.interpss.dist.DistObjectFactory;

/**
 * Programmatic replacements for test-only networks that were formerly loaded
 * through the removed IEEE_ODM adapter.
 */
public final class LegacyNetworkFixtures {
    private static final Complex ZERO_Z = new Complex(0.0, 1.0e-8);

    private LegacyNetworkFixtures() {
    }

    public static AclfNetwork ieee14Breaker() throws InterpssException {
        return createIeee14Breaker(false);
    }

    public static AclfNetwork ieee14BreakerLoop() throws InterpssException {
        return createIeee14Breaker(true);
    }

    private static AclfNetwork createIeee14Breaker(boolean loop) throws InterpssException {
        AclfNetwork net = new IeeeCDFDirectParser()
                .parse("testData/adpter/ieee_format/ieee14_comma.ieee");
        AclfNetworkBuilder builder = new AclfNetworkBuilder(net);

        removeBranches(net,
                "Bus1->Bus2(1)", "Bus1->Bus5(1)", "Bus4->Bus7(1)",
                "Bus7->Bus8(1)", "Bus7->Bus9(1)", "Bus9->Bus14(1)",
                "Bus13->Bus14(1)");

        addBus(builder, "Bus71", 71, 35.0, 1.062, -13.37, "Bus 7     ZV");
        addBus(builder, "Bus72", 72, 35.0, 1.062, -13.37, "Bus 7     ZV");
        addBus(builder, "Bus73", 73, 35.0, 1.062, -13.37, "Bus 7     ZV");
        addBus(builder, "Bus15", 15, 132.0, 1.06, 0.0, "Bus 15    HV");
        addBus(builder, "Bus16", 16, 132.0, 1.06, 0.0, "Bus 16    HV");
        addBus(builder, "Bus17", 17, 35.0, 1.036, -16.04, "Bus 17    LV");
        addBus(builder, "Bus18", 18, 35.0, 1.036, -16.04, "Bus 18    LV");
        net.getBus("Bus7").setGenCode(AclfGenCode.NON_GEN);

        if (loop) {
            addBus(builder, "Bus74", 74, 35.0, 1.062, -13.37, "Bus 7     ZV");
            addBus(builder, "Bus14_1", 14, 35.0, 1.036, -16.04, "Bus 14    LV");
            net.getBus("Bus14").getContributeLoadList().get(0)
                    .setLoadCP(new Complex(0.0745, 0.025));
            builder.addContributeLoad("Bus14_1", "load14_1", true,
                    new Complex(0.0745, 0.025), null, null, null, false);
            addZeroZ(builder, "Bus1", "Bus15");
            addZeroZ(builder, "Bus71", "Bus74");
            addZeroZ(builder, "Bus72", "Bus74");
            addZeroZ(builder, "Bus14", "Bus14_1");
        } else {
            addBus(builder, "Bus15-1", 15, 132.0, 1.06, 0.0, "Bus 15    HV");
            addZeroZ(builder, "Bus1", "Bus15-1");
            addZeroZ(builder, "Bus15-1", "Bus15");
        }

        addLine(builder, "Bus15", "Bus2", 0.01938, 0.05917, 0.0528);
        addZeroZ(builder, "Bus1", "Bus16");
        addLine(builder, "Bus16", "Bus5", 0.05403, 0.22304, 0.0492);
        addXfr(builder, "Bus4", "Bus73", 0.20912, 0.978);
        addXfr(builder, "Bus71", "Bus8", 0.17615, 1.0);
        addXfr(builder, "Bus72", "Bus9", 0.11001, 1.0);
        addZeroZ(builder, "Bus73", "Bus7");
        addZeroZ(builder, "Bus72", "Bus7");
        addZeroZ(builder, "Bus71", "Bus7");
        addLine(builder, "Bus9", "Bus17", 0.12711, 0.27038, 0.0);
        addZeroZ(builder, "Bus17", "Bus14");
        addLine(builder, "Bus13", "Bus18", 0.17093, 0.34802, 0.0);
        addZeroZ(builder, "Bus18", "Bus14");

        net.initContributeGenLoad(false);
        return net;
    }

    public static AclfNetwork aclfChildNetwork() {
        AclfNetwork parent = CoreObjectFactory.createAclfNetwork();
        ChildNetworkWrapper<AclfBus, AclfBranch> wrapper = ChildNetObjectFactory.createChildAclfNet(
                parent, "AclfChileNet1", ChildNetInterfaceType.BRANCH_INTERFACE);
        addInterface(wrapper, "Bus2-Bus1", "ChildBus-1");
        addInterface(wrapper, "Bus4-Bus3", "ChildBus-2");
        return parent;
    }

    public static AclfNetwork distChildNetwork(boolean includeDcChild) {
        AclfNetwork parent = CoreObjectFactory.createAclfNetwork();
        ChildNetworkWrapper<DistBus, DistBranch> wrapper =
                DistObjectFactory.createChildDistNet(parent, "DistChileNet1");
        wrapper.setInterfaceType(ChildNetInterfaceType.BRANCH_INTERFACE);
        addInterface(wrapper, "Bus2-Bus1", "DistBus-1");
        addInterface(wrapper, "Bus4-Bus3", "DistBus-2");

        if (includeDcChild) {
            DistNetwork distNet = (DistNetwork) wrapper.getNetwork();
            ChildNetworkWrapper<?, ?> dcWrapper =
                    DistObjectFactory.createChildDcSysNet(distNet, "ChildDcSysteNet1");
            dcWrapper.setInterfaceType(ChildNetInterfaceType.BRANCH_INTERFACE);
            addInterface(dcWrapper, "DistBranchId", "DcBus1");
        }
        return parent;
    }

    private static void removeBranches(AclfNetwork net, String... branchIds) {
        for (String branchId : branchIds) {
            if (!net.removeBranch(branchId)) {
                throw new IllegalStateException("Branch not found in IEEE 14 fixture: " + branchId);
            }
        }
    }

    private static void addBus(AclfNetworkBuilder builder, String id, int number,
            double baseKv, double voltage, double angleDeg, String name) throws InterpssException {
        builder.addBus(id, name, number, baseKv * 1000.0, voltage,
                Math.toRadians(angleDeg), "1", "1", null);
    }

    private static void addLine(AclfNetworkBuilder builder, String fromBusId, String toBusId,
            double r, double x, double totalB) throws InterpssException {
        builder.addLine(fromBusId, toBusId, "1", new Complex(r, x),
                new Complex(0.0, totalB * 0.5), null, null,
                0.0, 0.0, 0.0, true);
    }

    private static void addXfr(AclfNetworkBuilder builder, String fromBusId, String toBusId,
            double x, double fromTap) throws InterpssException {
        builder.addXformer2W(fromBusId, toBusId, "1", new Complex(0.0, x),
                fromTap, 1.0, null, null, 0.0, 0.0, 0.0, 0, true);
    }

    private static void addZeroZ(AclfNetworkBuilder builder, String fromBusId, String toBusId)
            throws InterpssException {
        builder.addBreaker(fromBusId, toBusId, "1", ZERO_Z, true, AclfBranchCode.LINE);
    }

    private static void addInterface(ChildNetworkWrapper<?, ?> wrapper,
            String branchId, String childBusId) {
        ChildNetInterfaceBranch intBranch = ChildNetObjectFactory.createChildNetInerfaceBranch(wrapper);
        intBranch.setBranchId(branchId);
        intBranch.setInterfaceBusSide(BranchBusSide.TO_SIDE);
        intBranch.setChildNetSide(BranchBusSide.TO_SIDE);
        intBranch.setInterfaceBusIdChildNet(childBusId);
    }
}
