package org.interpss.fadapter.builder;

import org.interpss.dstab.control.exc.ExciterObjectFactory;
import org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.dc1.IEEE1981DC1Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciterData;
import org.interpss.dstab.control.exc.simple.SimpleExciter;
import org.interpss.dstab.control.gov.GovernorObjectFactory;
import org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor;
import org.interpss.dstab.control.gov.psse.gast.PsseGASTGasTurGovernor;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1GovernorData;
import org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernor;
import org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernorData;
import org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.dstab.control.gov.simple.SimpleGovernor;
import org.interpss.dstab.control.pss.StabilizerObjectFactory;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer;
import org.interpss.dstab.control.pss.ieee.y1992.pss1a.Ieee1992PSS1AStabilizer;
import org.interpss.dstab.renewable.Reecb1Data;
import org.interpss.dstab.renewable.Reecb1Model;
import org.interpss.dstab.renewable.Reeca1Data;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Data;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Regfma1Data;
import org.interpss.dstab.renewable.Regfma1Model;
import org.interpss.dstab.renewable.Repca1Data;
import org.interpss.dstab.renewable.Repca1Model;
import org.interpss.dstab.renewable.WindControlStack;
import org.interpss.dstab.renewable.Wtara1Data;
import org.interpss.dstab.renewable.Wtara1Model;
import org.interpss.dstab.renewable.Wtpta1Data;
import org.interpss.dstab.renewable.Wtpta1Model;
import org.interpss.dstab.renewable.Wttqa1Data;
import org.interpss.dstab.renewable.Wttqa1Model;
import org.interpss.dstab.mach.GenqecData;
import org.interpss.dstab.mach.GenqecMachine;
import org.interpss.dstab.mach.GenqejData;
import org.interpss.dstab.mach.GenqejMachine;
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
import com.interpss.core.acsc.AcscFactory;

public class DStabNetworkBuilder {
    private static final Logger log = LoggerFactory.getLogger(DStabNetworkBuilder.class);

    private final BaseDStabNetwork<?, ?> network;

    public DStabNetworkBuilder(BaseDStabNetwork<?, ?> network) {
        this.network = network;
    }

    /**
     * Legacy accessor for the standard positive-sequence network type.
     */
    public DStabilityNetwork getDStabNetwork() {
        return (DStabilityNetwork) network;
    }

    /**
     * Accessor for standard and specialized DStab network implementations.
     */
    public BaseDStabNetwork<?, ?> getBaseDStabNetwork() {
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
        String machId = busId + "-mach" + genId;
        RoundRotorMachine mach = (RoundRotorMachine) DStabObjectFactory.createMachine(
                machId, "GENROU", MachineModelType.EQ11_ED11_ROUND_ROTOR,
                network, busId, genId);

        mach.setRating(ratingMva, UnitType.mVA, network.getBaseKva());
        mach.setRatedVoltage(ratedKv, UnitType.kV);
        mach.calMultiFactors();
        mach.setPoles(2);
        mach.setH(h);
        mach.setD(toCoreDamping(d));
        mach.setRa(sourceResistanceOnMachineBase(mach));
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
     * WECC GENQEC model with saturation applied to all mutual inductances.
     * Parameters are corrected according to the published PowerWorld rules.
     *
     * @return the created machine, or null if the bus was not found
     */
    public GenqecMachine addGenqec(String busId, String genId,
            double ratingMva, double ratedKv, GenqecData inputData) throws InterpssException {
        BaseDStabBus<?, ?> bus = network.getDStabBus(busId);
        if (bus == null) {
            log.warn("Bus not found for GENQEC: {}", busId);
            return null;
        }

        GenqecMachine mach = new GenqecMachine(inputData);
        configureGenqeMachine(mach, "GENQEC", busId, genId, ratingMva, ratedKv, inputData);
        return mach;
    }

    /**
     * WECC GENQEJ model. Its dynamic equations reuse GENQEC and the core
     * round-rotor machine; only the published KIS saturation input differs.
     */
    public GenqejMachine addGenqej(String busId, String genId,
            double ratingMva, double ratedKv, GenqejData inputData) throws InterpssException {
        BaseDStabBus<?, ?> bus = network.getDStabBus(busId);
        if (bus == null) {
            log.warn("Bus not found for GENQEJ: {}", busId);
            return null;
        }

        GenqejMachine mach = new GenqejMachine(inputData);
        configureGenqeMachine(mach, "GENQEJ", busId, genId, ratingMva, ratedKv,
                mach.getGenqecData());
        return mach;
    }

    private void configureGenqeMachine(GenqecMachine mach, String modelName,
            String busId, String genId, double ratingMva, double ratedKv,
            GenqecData inputData) throws InterpssException {
        mach.setId(busId + "-mach" + genId);
        mach.setName(modelName);
        mach.setMachType(MachineModelType.EQ11_ED11_ROUND_ROTOR);
        mach.setMachData(DStabObjectFactory.createMachineData());
        mach.getMachData().setGrounding(AcscFactory.eINSTANCE.createBusScGrounding());
        network.addMachine(mach, busId, genId);

        mach.setRating(ratingMva, UnitType.mVA, network.getBaseKva());
        mach.setRatedVoltage(ratedKv, UnitType.kV);
        mach.calMultiFactors();
        mach.setPoles(2);
        mach.setH(inputData.h());
        mach.setD(toCoreDamping(inputData.d()));
        mach.setRa(inputData.ra());
        mach.setXl(inputData.xl());
        mach.setXd(inputData.xd());
        mach.setXq(inputData.xq());
        mach.setXd1(inputData.xdp());
        mach.setXq1(inputData.xqp());
        mach.setXd11(inputData.xdpp());
        mach.setXq11(inputData.xqpp());
        mach.setTd01(inputData.tdop());
        mach.setTq01(inputData.tqop());
        mach.setTd011(inputData.tdopp());
        mach.setTq011(inputData.tqopp());
        // GENQEC owns its saturation function. Keep the inherited percentage
        // fields zero so core initialization uses this model's adjusted Xq path.
        mach.setSe100(0.0);
        mach.setSe120(0.0);
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
        String machId = busId + "-mach" + genId;
        SalientPoleMachine mach = (SalientPoleMachine) DStabObjectFactory.createMachine(
                machId, "GENSAL", MachineModelType.EQ11_SALIENT_POLE,
                (BaseDStabNetwork<?, ?>) network, busId, genId);

        mach.setRating(ratingMva, UnitType.mVA, network.getBaseKva());
        mach.setRatedVoltage(ratedKv, UnitType.kV);
        mach.calMultiFactors();
        mach.setPoles(2);
        mach.setH(h);
        mach.setD(toCoreDamping(d));
        mach.setRa(sourceResistanceOnMachineBase(mach));
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
        String machId = busId + "-mach" + genId;
        Eq1Ed1Machine mach = (Eq1Ed1Machine) DStabObjectFactory.createMachine(
                machId, "EQ1ED1", MachineModelType.EQ1_ED1_MODEL,
                (BaseDStabNetwork<?, ?>) network, busId, genId);

        mach.setRating(ratingMva, UnitType.mVA, network.getBaseKva());
        mach.setRatedVoltage(ratedKv, UnitType.kV);
        mach.calMultiFactors();
        mach.setPoles(2);
        mach.setH(h);
        mach.setD(toCoreDamping(d));
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
        String machId = busId + "-mach" + genId;
        Eq1Machine mach = (Eq1Machine) DStabObjectFactory.createMachine(
                machId, "EQ1", MachineModelType.EQ1_MODEL,
                (BaseDStabNetwork<?, ?>) network, busId, genId);

        mach.setRating(ratingMva, UnitType.mVA, network.getBaseKva());
        mach.setRatedVoltage(ratedKv, UnitType.kV);
        mach.calMultiFactors();
        mach.setPoles(2);
        mach.setH(h);
        mach.setD(toCoreDamping(d));
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
        String machId = busId + "-mach" + genId;
        EConstMachine mach = (EConstMachine) DStabObjectFactory.createMachine(
                machId, "GENCLS", MachineModelType.ECONSTANT,
                (BaseDStabNetwork<?, ?>) network, busId, genId);

        mach.setRating(ratingMva, UnitType.mVA, network.getBaseKva());
        mach.setRatedVoltage(ratedKv, UnitType.kV);
        mach.calMultiFactors();
        mach.setPoles(2);
        mach.setH(h);
        mach.setD(toCoreDamping(d));
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
        String machId = busId + "-mach" + genId;
        return DStabObjectFactory.createInfiniteMachine(
                machId, "InfiniteBus",
                (BaseDStabNetwork<?, ?>) network, busId, genId);
    }

    // ==================== Stabilizer Models ====================

    /** PSS1A single-input stabilizer supporting all six documented local input codes. */
    public Ieee1992PSS1AStabilizer addPss1a(String busId, String genId,
            int ics, double a1, double a2,
            double t1, double t2, double t3, double t4, double t5, double t6,
            double ks, double lsmax, double lsmin, double vcu, double vcl) {
        Machine machine = findMachine(busId, genId);
        if (machine == null) {
            log.warn("Machine not found for PSS1A: {} {}", busId, genId);
            return null;
        }
        if (ics < 1 || ics > 6) {
            log.warn("PSS1A input code is not implemented at {} {}: ICS={}", busId, genId, ics);
            return null;
        }
        if (ics == 6 && t6 <= 0.0) {
            log.warn("PSS1A voltage-derivative input requires T6 > 0 at {} {}", busId, genId);
            return null;
        }
        Ieee1992PSS1AStabilizer pss = StabilizerObjectFactory
                .createIeee1992PSS1AStabilizer(busId + "-pss1a" + genId, "PSS1A", machine);
        var data = pss.getData();
        data.setIcs(ics);
        data.setA1(a1);
        data.setA2(a2);
        data.setT1(t1);
        data.setT2(t2);
        data.setT3(t3);
        data.setT4(t4);
        data.setT5(t5);
        data.setT6(t6);
        data.setKs(ks);
        data.setVstmax(lsmax);
        data.setVstmin(lsmin);
        data.setVcu(vcu);
        data.setVcl(vcl);
        return pss;
    }

    /**
     * PSS2A IEEE dual-input stabilizer. The current CML signal path implements
     * local rotor-speed input 1 and local electrical-power input 2, which is
     * the sole selector combination present in all six Texas2k cases.
     */
    public Ieee1992PSS2AStabilizer addPss2a(String busId, String genId,
            int ics1, int remoteBus1, int ics2, int remoteBus2, int m, int n,
            double tw1, double tw2, double t6, double tw3, double tw4, double t7,
            double ks2, double ks3, double t8, double t9, double ks1,
            double t1, double t2, double t3, double t4, double vstmax, double vstmin) {
        Machine machine = findMachine(busId, genId);
        if (machine == null) {
            log.warn("Machine not found for PSS2A: {} {}", busId, genId);
            return null;
        }
        if (ics1 != 1 || remoteBus1 != 0 || ics2 != 3 || remoteBus2 != 0) {
            log.warn("PSS2A selector combination is not implemented at {} {}: "
                    + "ICS1={}, REMBUS1={}, ICS2={}, REMBUS2={}",
                    busId, genId, ics1, remoteBus1, ics2, remoteBus2);
            return null;
        }
        Ieee1992PSS2AStabilizer pss = StabilizerObjectFactory
                .createIeee1992PSS2AStabilizer(busId + "-pss2a" + genId, "PSS2A", machine);
        var data = pss.getData();
        data.setIcs1(ics1);
        data.setRemoteBus1(remoteBus1);
        data.setIcs2(ics2);
        data.setRemoteBus2(remoteBus2);
        data.setM(m);
        data.setN(n);
        data.setTw1(tw1);
        data.setTw2(tw2);
        data.setT6(t6);
        data.setTw3(tw3);
        data.setTw4(tw4);
        data.setT7(t7);
        data.setKs2(ks2);
        data.setKs3(ks3);
        data.setT8(t8);
        data.setT9(t9);
        data.setKs1(ks1);
        data.setT1(t1);
        data.setT2(t2);
        data.setT3(t3);
        data.setT4(t4);
        data.setVstmax(vstmax);
        data.setVstmin(vstmin);
        return pss;
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
     * PSS/E ESST1A exciter with the complete 20-parameter record schema.
     */
    public IEEE1981ST1Exciter addExcEsst1a(String busId, String genId,
            int uel, int vos, double tr, double vimax, double vimin,
            double tc, double tb, double tc1, double tb1,
            double ka, double ta, double vamax, double vamin,
            double vrmax, double vrmin, double kc, double kf, double tf,
            double klr, double ilr) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for ESST1A exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        IEEE1981ST1Exciter exc = ExciterObjectFactory.createIeee1981ST1Exciter(
                mach.getId() + "_Exc", "ESST1A", mach);
        var data = exc.getData();
        data.setUel(uel); data.setVos(vos); data.setTr(tr);
        data.setVimax(vimax); data.setVimin(vimin);
        data.setTc(tc); data.setTb(tb); data.setTc1(tc1); data.setTb1(tb1);
        data.setKa(ka); data.setTa(ta); data.setVamax(vamax); data.setVamin(vamin);
        data.setVrmax(vrmax); data.setVrmin(vrmin); data.setKc(kc);
        data.setKf(kf); data.setTf(tf); data.setKlr(klr); data.setIlr(ilr);
        return exc;
    }

    /** PSS/E ESDC2A exciter. */
    public org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aExciter addExcEsdc2a(
            String busId, String genId,
            double tr, double ka, double ta, double tc, double tb,
            double vrmax, double vrmin, double ke, double te,
            double kf, double tf, double e1, double se1, double e2, double se2) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) return null;
        var data = new org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aData();
        data.setTr(tr); data.setKa(ka); data.setTa(ta); data.setTc(tc); data.setTb(tb);
        data.setVrmax(vrmax); data.setVrmin(vrmin); data.setKe(ke); data.setTe(te);
        data.setKf(kf); data.setTf(tf); data.setE1(e1); data.setSe1(se1);
        data.setE2(e2); data.setSe2(se2);
        var exc = new org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aExciter(
                mach.getId() + "_Exc", data, mach);
        return exc;
    }

    /** PSS/E ESST3A mapped to the existing IEEE 2005 ST3A implementation. */
    public org.interpss.dstab.control.exc.ieee.y2005.st3a.IEEE2005ST3AExciter addExcEsst3a(
            String busId, String genId, double tr, double vimax, double vimin,
            double km, double tc, double tb, double ka, double ta,
            double vrmax, double vrmin, double kg, double kp, double ki,
            double vbmax, double kc, double xl, double vgmax, double thetaP,
            double tm, double vmmax, double vmmin) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) return null;
        var exc = ExciterObjectFactory.createIeee2005ST3AExciter(
                mach.getId() + "_Exc", "ESST3A", mach);
        var data = exc.getData();
        data.setTr(tr); data.setVimax(vimax); data.setVimin(vimin);
        data.setKm(km); data.setTc(tc); data.setTb(tb);
        data.setKa(ka); data.setTa(ta); data.setVrmax(vrmax); data.setVrmin(vrmin);
        data.setKg(kg); data.setKp(kp); data.setKi(ki); data.setVbmax(vbmax);
        data.setKc(kc); data.setXl(xl); data.setVgmax(vgmax);
        data.setAngKp(thetaP); data.setTm(tm); data.setVmmax(vmmax); data.setVmmin(vmmin);
        return exc;
    }

    /** Attach the 17-parameter PSS/E ESST4B exciter. */
    public IEEE2005ST4BExciter addExcEsst4b(String busId, String genId,
            IEEE2005ST4BExciterData source) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) return null;
        IEEE2005ST4BExciter exc = ExciterObjectFactory.createIeee2005ST4BExciter(
                mach.getId() + "_Exc", "ESST4B", mach);
        IEEE2005ST4BExciterData target = exc.getData();
        target.setTr(source.getTr()); target.setKpr(source.getKpr());
        target.setKir(source.getKir()); target.setVrmax(source.getVrmax());
        target.setVrmin(source.getVrmin()); target.setTa(source.getTa());
        target.setKpm(source.getKpm()); target.setKim(source.getKim());
        target.setVmmax(source.getVmmax()); target.setVmmin(source.getVmmin());
        target.setKg(source.getKg()); target.setKp(source.getKp());
        target.setKi(source.getKi()); target.setVbmax(source.getVbmax());
        target.setKc(source.getKc()); target.setXl(source.getXl());
        target.setAngKp(source.getAngKp()); target.setVgmax(source.getVgmax());
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

    /** Attach a PSS/E HYGOV hydro governor. */
    public PsseHygovGovernor addGovHygov(String busId, String genId,
            PsseHygovGovernorData data) {
        return addGovHygov(busId, genId, data, "HYGOV");
    }

    public PsseHygovGovernor addGovHygovd(String busId, String genId,
            PsseHygovGovernorData data) {
        return addGovHygov(busId, genId, data, "HYGOVD");
    }

    private PsseHygovGovernor addGovHygov(String busId, String genId,
            PsseHygovGovernorData data, String modelName) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for {} governor: bus={}, gen={}", modelName, busId, genId);
            return null;
        }
        PsseHygovGovernor gov = new PsseHygovGovernor(
                mach.getId() + "_Gov", modelName, "PSS/E");
        copyHygovData(data, gov.getData());
        if (!gov.validateParameters()) {
            log.warn("Invalid HYGOV parameters: bus={}, gen={}", busId, genId);
            return null;
        }
        gov.setMachine(mach);
        return gov;
    }

    private static void copyHygovData(PsseHygovGovernorData source,
            PsseHygovGovernorData target) {
        target.setR(source.getR()); target.setRtemp(source.getRtemp());
        target.setTr(source.getTr()); target.setTf(source.getTf());
        target.setTg(source.getTg()); target.setVelm(source.getVelm());
        target.setGmax(source.getGmax()); target.setGmin(source.getGmin());
        target.setTw(source.getTw()); target.setAt(source.getAt());
        target.setDturb(source.getDturb()); target.setQnl(source.getQnl());
        target.setDbH(source.getDbH()); target.setDbL(source.getDbL());
        target.setTrate(source.getTrate());
    }

    /** Attach a PSS/E GGOV1 governor after its record has been mapped exactly. */
    public PsseGgov1Governor addGovGgov1(String busId, String genId,
            PsseGgov1GovernorData data) {
        return addGovGgov1(busId, genId, data, "GGOV1");
    }

    /** Attach a PSS/E GGOV1D/GGOV1DU governor with input-frequency deadband. */
    public PsseGgov1Governor addGovGgov1d(String busId, String genId,
            PsseGgov1GovernorData data) {
        return addGovGgov1(busId, genId, data, "GGOV1D");
    }

    private PsseGgov1Governor addGovGgov1(String busId, String genId,
            PsseGgov1GovernorData data, String modelName) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for {} governor: bus={}, gen={}", modelName, busId, genId);
            return null;
        }
        PsseGgov1Governor gov = new PsseGgov1Governor(
                mach.getId() + "_Gov", modelName, "PSS/E");
        copyGgov1Data(data, gov.getData());
        if (!gov.validateParameters()) {
            log.warn("Unsupported or invalid {} parameters: bus={}, gen={}", modelName, busId, genId);
            return null;
        }
        gov.setMachine(mach);
        return gov;
    }

    private static void copyGgov1Data(PsseGgov1GovernorData source, PsseGgov1GovernorData target) {
        target.setRselect(source.getRselect()); target.setFlag(source.getFlag());
        target.setR(source.getR()); target.setTpelec(source.getTpelec());
        target.setMaxerr(source.getMaxerr()); target.setMinerr(source.getMinerr());
        target.setKpgov(source.getKpgov()); target.setKigov(source.getKigov());
        target.setKdgov(source.getKdgov()); target.setTdgov(source.getTdgov());
        target.setVmax(source.getVmax()); target.setVmin(source.getVmin());
        target.setTact(source.getTact()); target.setKturb(source.getKturb());
        target.setWfnl(source.getWfnl()); target.setTb(source.getTb()); target.setTc(source.getTc());
        target.setTeng(source.getTeng()); target.setTfload(source.getTfload());
        target.setKpload(source.getKpload()); target.setKiload(source.getKiload());
        target.setLdref(source.getLdref()); target.setDm(source.getDm());
        target.setRopen(source.getRopen()); target.setRclose(source.getRclose());
        target.setKimw(source.getKimw()); target.setAset(source.getAset());
        target.setKa(source.getKa()); target.setTa(source.getTa()); target.setTrate(source.getTrate());
        target.setDb(source.getDb()); target.setTsa(source.getTsa()); target.setTsb(source.getTsb());
        target.setRup(source.getRup()); target.setRdown(source.getRdown());
        target.setDbH(source.getDbH()); target.setDbL(source.getDbL());
    }

    /**
     * PSS/E TGOV1 steam turbine governor.
     * Parameters: R, T1, VMAX, VMIN, T2, T3, Dt
     *
     * @return the created governor, or null if the machine was not found
     */
    public PsseTGov1SteamTurGovernor addGovTgov1(String busId, String genId,
            double r, double t1, double vmax, double vmin,
            double t2, double t3, double dt) {
        if (r <= 0.0) {
            log.warn("Invalid TGOV1 droop at {} {}: R={}", busId, genId, r);
            return null;
        }
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

    public PsseIEESGOSteamTurGovernor addGovIeesgod(String busId, String genId,
            double t1, double t2, double t3, double t4, double t5, double t6,
            double k1, double k2, double k3, double pmax, double pmin,
            double dbH, double dbL, double trate) {
        if (dbH < 0.0 || dbL > 0.0 || dbL > dbH || trate < 0.0) return null;
        PsseIEESGOSteamTurGovernor gov = addGovIeesgo(busId, genId,
                t1, t2, t3, t4, t5, t6, k1, k2, k3, pmax, pmin);
        if (gov == null) return null;
        gov.setName("IEESGOD");
        gov.getData().setDbH(dbH); gov.getData().setDbL(dbL);
        gov.getData().setTrate(trate);
        return gov;
    }

    /** PSS/E IEEEG1D/IEEEG1SDU with a single combined mechanical-power output. */
    public IeeeSteamTCDRGovernor addGovIeeeg1d(String busId, String genId,
            double k, double t1, double t2, double t3, double uo, double uc,
            double pmax, double pmin, double t4, double k1, double k2,
            double t5, double k3, double k4, double t6, double k5, double k6,
            double t7, double k7, double k8, double dbH, double dbL, double trate) {
        double fractionSum = k1 + k2 + k3 + k4 + k5 + k6 + k7 + k8;
        if (k <= 0.0 || t3 <= 0.0 || fractionSum <= 0.0
                || dbH < 0.0 || dbL > 0.0 || dbL > dbH || trate < 0.0) {
            log.warn("Invalid IEEEG1D parameters at {} {}", busId, genId);
            return null;
        }
        IeeeSteamTCDRGovernor gov = addGovIeeeg1(busId, genId, k, t1, t2, t3,
                k1 + k2, k3 + k4, t4, k5 + k6, t5, k7 + k8, t6, t7,
                uc, uo, pmax, pmin);
        if (gov == null) return null;
        gov.setName("IEEEG1D");
        gov.getData().setDbH(dbH);
        gov.getData().setDbL(dbL);
        gov.getData().setTrate(trate);
        return gov;
    }

    /** PSS/E TGOV1D with asymmetric speed deadband and turbine MW rating. */
    public PsseTGov1SteamTurGovernor addGovTgov1d(String busId, String genId,
            double r, double t1, double vmax, double vmin,
            double t2, double t3, double dt, double dbH, double dbL, double trate) {
        if (dbH < 0.0 || dbL > 0.0 || dbL > dbH || trate < 0.0) {
            log.warn("Invalid TGOV1D deadband/rating at {} {}: dbH={}, dbL={}, Trate={}",
                    busId, genId, dbH, dbL, trate);
            return null;
        }
        PsseTGov1SteamTurGovernor gov = addGovTgov1(
                busId, genId, r, t1, vmax, vmin, t2, t3, dt);
        if (gov == null) return null;
        gov.setName("TGOV1D");
        gov.getData().setDbH(dbH);
        gov.getData().setDbL(dbL);
        gov.getData().setTrate(trate);
        return gov;
    }

    // ==================== Renewable Models ====================

    public Regca1Model addRegca1(String busId, String genId, Regca1Data data) {
        BaseDStabBus<?, ?> bus = network.getDStabBus(busId);
        DStabGen gen = bus == null ? null : (DStabGen) bus.getContributeGen(genId);
        if (gen == null) {
            log.warn("Generator not found for REGCA1: bus={}, gen={}", busId, genId);
            return null;
        }
        return new Regca1Model(gen, bus, genId, data);
    }

    public Regfma1Model addRegfma1(String busId, String genId, Regfma1Data data) {
        BaseDStabBus<?, ?> bus = network.getDStabBus(busId);
        DStabGen gen = bus == null ? null : (DStabGen) bus.getContributeGen(genId);
        if (gen == null) {
            log.warn("Generator not found for REGFMA1: bus={}, gen={}", busId, genId);
            return null;
        }
        return new Regfma1Model(gen, bus, genId, data);
    }

    public Reecb1Model addReecb1(String busId, String genId, Reecb1Data data) {
        Regca1Model converter = findRegca1(busId, genId);
        if (converter == null) {
            log.warn("REGCA1 not found for REECB1: bus={}, gen={}", busId, genId);
            return null;
        }
        Reecb1Model controller = new Reecb1Model(data, converter);
        converter.setElectricalController(controller);
        return controller;
    }

    public Reeca1Model addReeca1(String busId, String genId, Reeca1Data data) {
        Regca1Model converter = findRegca1(busId, genId);
        if (converter == null) {
            log.warn("REGCA1 not found for REECA1: bus={}, gen={}", busId, genId);
            return null;
        }
        Reeca1Model controller = new Reeca1Model(data, converter);
        converter.setReeca1Controller(controller);
        return controller;
    }

    public Repca1Model addRepca1(String busId, String genId, Repca1Data data) {
        Regca1Model converter = findRegca1(busId, genId);
        if (converter == null || converter.getActiveElectricalController() == null) {
            log.warn("REGCA1/REEC chain not found for REPCA1: bus={}, gen={}", busId, genId);
            return null;
        }
        Repca1Model controller = new Repca1Model(data, converter);
        converter.getActiveElectricalController().setPlantController(controller);
        return controller;
    }

    public Wtara1Model addWtara1(String busId, String genId, Wtara1Data data) {
        Reeca1Model controller = findReeca1(busId, genId);
        if (controller == null) {
            log.warn("REECA1 not found for WTARA1: bus={}, gen={}", busId, genId);
            return null;
        }
        Wtara1Model model = new Wtara1Model(data);
        windStack(controller).setAerodynamics(model);
        return model;
    }

    public Wtpta1Model addWtpta1(String busId, String genId, Wtpta1Data data) {
        Reeca1Model controller = findReeca1(busId, genId);
        if (controller == null) {
            log.warn("REECA1 not found for WTPTA1: bus={}, gen={}", busId, genId);
            return null;
        }
        Wtpta1Model model = new Wtpta1Model(data);
        windStack(controller).setPitchController(model);
        return model;
    }

    public Wttqa1Model addWttqa1(String busId, String genId, Wttqa1Data data) {
        Reeca1Model controller = findReeca1(busId, genId);
        if (controller == null) {
            log.warn("REECA1 not found for WTTQA1: bus={}, gen={}", busId, genId);
            return null;
        }
        Wttqa1Model model = new Wttqa1Model(data);
        windStack(controller).setTorqueController(model);
        return model;
    }

    private Regca1Model findRegca1(String busId, String genId) {
        BaseDStabBus<?, ?> bus = network.getDStabBus(busId);
        DStabGen gen = bus == null ? null : (DStabGen) bus.getContributeGen(genId);
        return gen != null && gen.getDynamicGenDevice() instanceof Regca1Model model ? model : null;
    }

    private Reeca1Model findReeca1(String busId, String genId) {
        Regca1Model converter = findRegca1(busId, genId);
        return converter == null ? null : converter.getReeca1Controller();
    }

    private static WindControlStack windStack(Reeca1Model controller) {
        WindControlStack stack = controller.getWindControlStack();
        if (stack == null) {
            stack = new WindControlStack();
            controller.setWindControlStack(stack);
        }
        return stack;
    }

    // ==================== Helpers ====================

    /**
     * Convert the PSS/E machine damping coefficient (pu torque / pu speed)
     * to the core machine convention, percent MW/Hz.  The core swing equation
     * converts the stored value back with {@code D * 0.01 * frequency}; passing
     * the PSS/E value through unchanged therefore introduces an erroneous
     * {@code frequency / 100} multiplier (0.6 at 60 Hz).
     */
    private double toCoreDamping(double psseDamping) {
        double frequency = network.getFrequency();
        return frequency > 0.0 ? psseDamping * 100.0 / frequency : psseDamping;
    }

    /**
     * PSS/E stores armature resistance with the static generator source
     * impedance. InterPSS machines store resistance on machine base.
     */
    private double sourceResistanceOnMachineBase(Machine machine) {
        if (machine.getParentGen().getSourceZ() == null
                || machine.getZMultiFactor() == 0.0) {
            return 0.0;
        }
        return machine.getParentGen().getSourceZ().getReal() / machine.getZMultiFactor();
    }

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
