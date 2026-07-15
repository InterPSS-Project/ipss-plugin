package org.interpss.fadapter.builder;

import org.interpss.dstab.control.exc.ExciterObjectFactory;
import org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.dc1.IEEE1981DC1Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.exc.simple.SimpleExciter;
import org.interpss.dstab.control.gov.GovernorObjectFactory;
import org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor;
import org.interpss.dstab.control.gov.psse.gast.PsseGASTGasTurGovernor;
import org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.dstab.control.gov.simple.SimpleGovernor;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.Eq1Ed1Machine;
import com.interpss.dstab.mach.Eq1Machine;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineModelType;
import com.interpss.dstab.mach.RoundRotorMachine;
import com.interpss.dstab.mach.SalientPoleMachine;

public class DStabNetworkBuilder {
    private static final Logger log = LoggerFactory.getLogger(DStabNetworkBuilder.class);

    private final DStabilityNetwork network;

    public DStabNetworkBuilder(DStabilityNetwork network) {
        this.network = network;
    }

    public DStabilityNetwork getDStabNetwork() {
        return network;
    }

    // ==================== Machine Models ====================

    /**
     * GENROU round-rotor generator (EQ11_ED11_ROUND_ROTOR).
     * Parameters follow PSS/E GENROU record order.
     *
     * @return the created machine, or null if the bus was not found
     */
    public RoundRotorMachine addGenrou(String busId, String genId,
            double ratingMva, double ratedKv,
            double td10, double td110, double tq10, double tq110,
            double h, double d,
            double xd, double xq, double xd1, double xq1, double xd11, double xl,
            double se100, double se120) throws InterpssException {
        BaseDStabBus<?, ?> bus = network.getDStabBus(busId);
        if (bus == null) {
            log.warn("Bus not found for GENROU: {}", busId);
            return null;
        }
        String machId = busId + "_" + genId + "_Mach";
        RoundRotorMachine mach = (RoundRotorMachine) DStabObjectFactory.createMachine(
                machId, "GENROU", MachineModelType.EQ11_ED11_ROUND_ROTOR,
                (BaseDStabNetwork<?, ?>) network, busId, genId);

        mach.setRating(ratingMva, UnitType.mVA, network.getBaseKva());
        mach.setRatedVoltage(ratedKv, UnitType.kV);
        mach.calMultiFactors();
        mach.setPoles(2);
        mach.setH(h);
        mach.setD(d);
        mach.setRa(0.0);
        mach.setXl(xl);
        mach.setXd(xd);
        mach.setXq(xq);
        mach.setXd1(xd1);
        mach.setXq1(xq1);
        mach.setTd01(td10);
        mach.setTq01(tq10);
        mach.setXd11(xd11);
        mach.setXq11(xd11);
        mach.setTd011(td110);
        mach.setTq011(tq110);
        mach.setSliner(0.85);
        mach.setSe100(se100);
        mach.setSe120(se120);
        return mach;
    }

    /**
     * GENSAL salient-pole generator (EQ11_SALIENT_POLE).
     * Parameters follow PSS/E GENSAL record order.
     *
     * @return the created machine, or null if the bus was not found
     */
    public SalientPoleMachine addGensal(String busId, String genId,
            double ratingMva, double ratedKv,
            double td10, double td110, double tq110,
            double h, double d,
            double xd, double xq, double xd1, double xd11, double xl,
            double se100, double se120) throws InterpssException {
        BaseDStabBus<?, ?> bus = network.getDStabBus(busId);
        if (bus == null) {
            log.warn("Bus not found for GENSAL: {}", busId);
            return null;
        }
        String machId = busId + "_" + genId + "_Mach";
        SalientPoleMachine mach = (SalientPoleMachine) DStabObjectFactory.createMachine(
                machId, "GENSAL", MachineModelType.EQ11_SALIENT_POLE,
                (BaseDStabNetwork<?, ?>) network, busId, genId);

        mach.setRating(ratingMva, UnitType.mVA, network.getBaseKva());
        mach.setRatedVoltage(ratedKv, UnitType.kV);
        mach.calMultiFactors();
        mach.setPoles(2);
        mach.setH(h);
        mach.setD(d);
        mach.setRa(0.0);
        mach.setXl(xl);
        mach.setXd(xd);
        mach.setXq(xq);
        mach.setXd1(xd1);
        mach.setTd01(td10);
        mach.setXd11(xd11);
        mach.setXq11(xd11);
        mach.setTd011(td110);
        mach.setTq011(tq110);
        mach.setSliner(0.85);
        mach.setSe100(se100);
        mach.setSe120(se120);
        return mach;
    }

    /**
     * GENROU with Eq1Ed1 model (4th-order, no sub-transient).
     * Parameters follow PSS/E GENROU record order but without sub-transient terms.
     *
     * @return the created machine, or null if the bus was not found
     */
    public Eq1Ed1Machine addEq1Ed1(String busId, String genId,
            double ratingMva, double ratedKv,
            double td10, double tq10,
            double h, double d,
            double xd, double xq, double xd1, double xq1, double xl,
            double se100, double se120) throws InterpssException {
        BaseDStabBus<?, ?> bus = network.getDStabBus(busId);
        if (bus == null) {
            log.warn("Bus not found for EQ1_ED1: {}", busId);
            return null;
        }
        String machId = busId + "_" + genId + "_Mach";
        Eq1Ed1Machine mach = (Eq1Ed1Machine) DStabObjectFactory.createMachine(
                machId, "EQ1ED1", MachineModelType.EQ1_ED1_MODEL,
                (BaseDStabNetwork<?, ?>) network, busId, genId);

        mach.setRating(ratingMva, UnitType.mVA, network.getBaseKva());
        mach.setRatedVoltage(ratedKv, UnitType.kV);
        mach.calMultiFactors();
        mach.setPoles(2);
        mach.setH(h);
        mach.setD(d);
        mach.setRa(0.0);
        mach.setXl(xl);
        mach.setXd(xd);
        mach.setXq(xq);
        mach.setXd1(xd1);
        mach.setXq1(xq1);
        mach.setTd01(td10);
        mach.setTq01(tq10);
        mach.setSliner(0.85);
        mach.setSe100(se100);
        mach.setSe120(se120);
        return mach;
    }

    /**
     * Eq1 model (3rd-order, single transient d-axis).
     *
     * @return the created machine, or null if the bus was not found
     */
    public Eq1Machine addEq1(String busId, String genId,
            double ratingMva, double ratedKv,
            double td10,
            double h, double d,
            double xd, double xq, double xd1, double xl,
            double se100, double se120) throws InterpssException {
        BaseDStabBus<?, ?> bus = network.getDStabBus(busId);
        if (bus == null) {
            log.warn("Bus not found for EQ1: {}", busId);
            return null;
        }
        String machId = busId + "_" + genId + "_Mach";
        Eq1Machine mach = (Eq1Machine) DStabObjectFactory.createMachine(
                machId, "EQ1", MachineModelType.EQ1_MODEL,
                (BaseDStabNetwork<?, ?>) network, busId, genId);

        mach.setRating(ratingMva, UnitType.mVA, network.getBaseKva());
        mach.setRatedVoltage(ratedKv, UnitType.kV);
        mach.calMultiFactors();
        mach.setPoles(2);
        mach.setH(h);
        mach.setD(d);
        mach.setRa(0.0);
        mach.setXl(xl);
        mach.setXd(xd);
        mach.setXq(xq);
        mach.setXd1(xd1);
        mach.setTd01(td10);
        mach.setSliner(0.85);
        mach.setSe100(se100);
        mach.setSe120(se120);
        return mach;
    }

    /**
     * GENCLS classical generator (constant E behind Xd').
     *
     * @return the created machine, or null if the bus was not found
     */
    public EConstMachine addGencls(String busId, String genId,
            double ratingMva, double ratedKv,
            double h, double d, double ra, double xd1) throws InterpssException {
        BaseDStabBus<?, ?> bus = network.getDStabBus(busId);
        if (bus == null) {
            log.warn("Bus not found for GENCLS: {}", busId);
            return null;
        }
        String machId = busId + "_" + genId + "_Mach";
        EConstMachine mach = (EConstMachine) DStabObjectFactory.createMachine(
                machId, "GENCLS", MachineModelType.ECONSTANT,
                (BaseDStabNetwork<?, ?>) network, busId, genId);

        mach.setRating(ratingMva, UnitType.mVA, network.getBaseKva());
        mach.setRatedVoltage(ratedKv, UnitType.kV);
        mach.calMultiFactors();
        mach.setPoles(2);
        mach.setH(h);
        mach.setD(d);
        mach.setRa(ra);
        mach.setXd1(xd1);
        return mach;
    }

    /**
     * Infinite bus machine (very large H, very small Xd').
     *
     * @return the created machine, or null if the bus was not found
     */
    public EConstMachine addInfiniteMachine(String busId, String genId) throws InterpssException {
        BaseDStabBus<?, ?> bus = network.getDStabBus(busId);
        if (bus == null) {
            log.warn("Bus not found for infinite machine: {}", busId);
            return null;
        }
        String machId = busId + "_" + genId + "_Mach";
        return DStabObjectFactory.createInfiniteMachine(
                machId, "InfiniteBus",
                (BaseDStabNetwork<?, ?>) network, busId, genId);
    }

    // ==================== Exciter Models ====================

    /**
     * IEEE 1968 Type 1 exciter (PSS/E IEEET1).
     * Parameters: TR, KA, TA, VRMAX, VRMIN, KE, TE, KF, TF, E1, SE1, E2, SE2
     *
     * @return the created exciter, or null if the machine was not found
     */
    public Ieee1968Type1Exciter addExcIeeet1(String busId, String genId,
            double tr, double ka, double ta, double vrmax, double vrmin,
            double ke, double te, double kf, double tf,
            double e1, double se1, double e2, double se2) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for IEEET1 exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        Ieee1968Type1Exciter exc = ExciterObjectFactory.createIeee1968Type1Exciter(
                mach.getId() + "_Exc", "IEEET1", mach);
        exc.getData().setTr(tr);
        exc.getData().setKa(ka);
        exc.getData().setTa(ta);
        exc.getData().setVrmax(vrmax);
        exc.getData().setVrmin(vrmin);
        exc.getData().setKe(ke);
        exc.getData().setTe(te);
        exc.getData().setKf(kf);
        exc.getData().setTf(tf);
        exc.getData().setE1(e1);
        exc.getData().setSeE1(se1);
        exc.getData().setE2(e2);
        exc.getData().setSeE2(se2);
        return exc;
    }

    /**
     * IEEE 1981 DC1 exciter (PSS/E ESDC1A).
     * Parameters: KA, TA, TC, TB, VRMAX, VRMIN, KE, TE, KF, TF, E1, SE1, E2, SE2
     *
     * @return the created exciter, or null if the machine was not found
     */
    public IEEE1981DC1Exciter addExcIeee1981Dc1(String busId, String genId,
            double ka, double ta, double tc, double tb,
            double vrmax, double vrmin,
            double ke, double te, double kf, double tf,
            double e1, double se1, double e2, double se2) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for IEEE1981DC1 exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        IEEE1981DC1Exciter exc = ExciterObjectFactory.createIeee1981DC1Exciter(
                mach.getId() + "_Exc", "ESDC1A", mach);
        exc.getData().setKa(ka);
        exc.getData().setTa(ta);
        exc.getData().setTc(tc);
        exc.getData().setTb(tb);
        exc.getData().setVrmax(vrmax);
        exc.getData().setVrmin(vrmin);
        exc.getData().setKe(ke);
        exc.getData().setTe(te == 0 ? 0.001 : te);
        exc.getData().setKf(kf);
        exc.getData().setTf(tf);
        exc.getData().setE1(e1);
        exc.getData().setSe_e1(se1);
        exc.getData().setE2(e2);
        exc.getData().setSe_e2(se2);
        return exc;
    }

    /**
     * IEEE 1981 ST1 exciter (PSS/E ESST1A).
     * Parameters: KA, TA, TC, TB, VRMAX, VRMIN, KF, TF, KC, VIMAX, VIMIN
     *
     * @return the created exciter, or null if the machine was not found
     */
    public IEEE1981ST1Exciter addExcIeee1981St1(String busId, String genId,
            double ka, double ta, double tc, double tb,
            double vrmax, double vrmin,
            double kf, double tf, double kc,
            double vimax, double vimin) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for IEEE1981ST1 exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        IEEE1981ST1Exciter exc = ExciterObjectFactory.createIeee1981ST1Exciter(
                mach.getId() + "_Exc", "ESST1A", mach);
        exc.getData().setKa(ka);
        exc.getData().setTa(ta);
        exc.getData().setTc(tc);
        exc.getData().setTb(tb);
        exc.getData().setVrmax(vrmax);
        exc.getData().setVrmin(vrmin);
        exc.getData().setKf(kf);
        exc.getData().setTf(tf);
        exc.getData().setKc(kc);
        exc.getData().setVimax(vimax);
        exc.getData().setVimin(vimin);
        return exc;
    }

    /**
     * Simple exciter with gain and time constant.
     *
     * @return the created exciter, or null if the machine was not found
     */
    public SimpleExciter addExcSimple(String busId, String genId,
            double ka, double ta, double vrmax, double vrmin) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for simple exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        SimpleExciter exc = ExciterObjectFactory.createSimpleExciter(
                mach.getId() + "_Exc", "SimpleExc", mach);
        exc.getData().setKa(ka);
        exc.getData().setTa(ta);
        exc.getData().setVrmax(vrmax);
        exc.getData().setVrmin(vrmin);
        return exc;
    }

    // ==================== Governor Models ====================

    /**
     * PSS/E TGOV1 steam turbine governor.
     * Parameters: R, T1, VMAX, VMIN, T2, T3, Dt
     *
     * @return the created governor, or null if the machine was not found
     */
    public PsseTGov1SteamTurGovernor addGovTgov1(String busId, String genId,
            double r, double t1, double vmax, double vmin,
            double t2, double t3, double dt) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for TGOV1 governor: bus={}, gen={}", busId, genId);
            return null;
        }
        PsseTGov1SteamTurGovernor gov = GovernorObjectFactory.createPsseTGOV1SteamTurGovernor(
                mach.getId() + "_Gov", "TGOV1", mach);
        gov.getData().setR(r);
        gov.getData().setT1(t1);
        gov.getData().setvMax(vmax);
        gov.getData().setvMin(vmin);
        gov.getData().setT2(t2);
        gov.getData().setT3(t3);
        gov.getData().setDt(dt);
        return gov;
    }

    /**
     * PSS/E GAST gas turbine governor.
     * Parameters: R, T1, T2, T3, AT, KT, VMAX, VMIN, DTURB
     *
     * @return the created governor, or null if the machine was not found
     */
    public PsseGASTGasTurGovernor addGovGast(String busId, String genId,
            double r, double t1, double t2, double t3,
            double at, double kt, double vmax, double vmin, double dturb) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for GAST governor: bus={}, gen={}", busId, genId);
            return null;
        }
        PsseGASTGasTurGovernor gov = GovernorObjectFactory.createPsseGASTGasTurGovernor(
                mach.getId() + "_Gov", "GAST", mach);
        gov.getData().setR(r);
        gov.getData().setT1(t1);
        gov.getData().setT2(t2);
        gov.getData().setT3(t3);
        gov.getData().setLoadLimit(at);
        gov.getData().setKt(kt);
        gov.getData().setVMax(vmax);
        gov.getData().setVMin(vmin);
        gov.getData().setDturb(dturb);
        return gov;
    }

    /**
     * PSS/E IEESGO steam turbine governor.
     * Parameters: T1, T2, T3, T4, T5, T6, K1, K2, K3, PMAX, PMIN
     *
     * @return the created governor, or null if the machine was not found
     */
    public PsseIEESGOSteamTurGovernor addGovIeesgo(String busId, String genId,
            double t1, double t2, double t3, double t4, double t5, double t6,
            double k1, double k2, double k3, double pmax, double pmin) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for IEESGO governor: bus={}, gen={}", busId, genId);
            return null;
        }
        PsseIEESGOSteamTurGovernor gov = GovernorObjectFactory.createPsseIEESGOSteamTurGovernor(
                mach.getId() + "_Gov", "IEESGO", mach);
        gov.getData().setT1(t1);
        gov.getData().setT2(t2);
        gov.getData().setT3(t3);
        gov.getData().setT4(t4);
        gov.getData().setT5(t5);
        gov.getData().setT6(t6);
        gov.getData().setK1(k1);
        gov.getData().setK2(k2);
        gov.getData().setK3(k3);
        gov.getData().setPmax(pmax);
        gov.getData().setPmin(pmin);
        return gov;
    }

    /**
     * IEEEG1 steam turbine governor (single PMech output, mapped to IeeeSteamTCDR).
     * Parameters: K, T1, T2, T3, K1(Fvhp), K3(Fhp), T4(Tch), K5(Fip), T5(Trh1),
     *             K7(Flp), T6(Trh2), T7(Tco), UC(VClose), UO(VOpen), PMAX, PMIN
     *
     * @return the created governor, or null if the machine was not found
     */
    public IeeeSteamTCDRGovernor addGovIeeeg1(String busId, String genId,
            double k, double t1, double t2, double t3,
            double fvhp, double fhp, double tch,
            double fip, double trh1, double flp, double trh2, double tco,
            double vclose, double vopen, double pmax, double pmin) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for IEEEG1 governor: bus={}, gen={}", busId, genId);
            return null;
        }
        IeeeSteamTCDRGovernor gov = GovernorObjectFactory.createIeeeSteamTCDRGovernor(
                mach.getId() + "_Gov", "IEEEG1", mach);
        gov.getData().setK(k);
        gov.getData().setT1(t1);
        gov.getData().setT2(t2);
        gov.getData().setT3(t3);
        gov.getData().setFvhp(fvhp);
        gov.getData().setFhp(fhp);
        gov.getData().setTch(tch);
        gov.getData().setFip(fip);
        gov.getData().setTrh1(trh1);
        gov.getData().setFlp(flp);
        gov.getData().setTrh2(trh2);
        gov.getData().setTco(tco);
        gov.getData().setPdown(vclose);
        gov.getData().setPup(vopen);
        gov.getData().setPmax(pmax);
        gov.getData().setPmin(pmin);
        return gov;
    }

    /**
     * Simple governor with gain and time constant.
     *
     * @return the created governor, or null if the machine was not found
     */
    public SimpleGovernor addGovSimple(String busId, String genId,
            double k, double t1, double pmax, double pmin) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for simple governor: bus={}, gen={}", busId, genId);
            return null;
        }
        SimpleGovernor gov = GovernorObjectFactory.createSimpleGovernor(
                mach.getId() + "_Gov", "SimpleGov", mach);
        gov.getData().setK(k);
        gov.getData().setT1(t1);
        gov.getData().setPmax(pmax);
        gov.getData().setPmin(pmin);
        return gov;
    }

    // ==================== Helpers ====================

    @SuppressWarnings("unchecked")
    private Machine findMachine(String busId, String genId) {
        BaseDStabBus<?, ?> bus = network.getDStabBus(busId);
        if (bus == null) {
            return null;
        }
        DStabGen gen = (DStabGen) bus.getContributeGen(genId);
        if (gen == null) {
            return null;
        }
        return gen.getMach();
    }
}
