package org.interpss.fadapter.builder;

import org.interpss.dstab.control.exc.ExciterObjectFactory;
import org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.dc1.IEEE1981DC1Exciter;
import org.interpss.dstab.control.exc.psse.ieeex1.Ieeex1Exciter;
import org.interpss.dstab.control.exc.psse.exdc2.Exdc2Exciter;
import org.interpss.dstab.control.exc.psse.exdc2a.Exdc2aExciter;
import org.interpss.dstab.control.exc.psse.ieeet4.Ieeet4Data;
import org.interpss.dstab.control.exc.psse.ieeet4.Ieeet4Exciter;
import org.interpss.dstab.control.exc.psse.ac7b.Ac7bData;
import org.interpss.dstab.control.exc.psse.ac7b.Ac7bExciter;
import org.interpss.dstab.control.exc.psse.ac1c.Ac1cData;
import org.interpss.dstab.control.exc.psse.ac1c.Ac1cExciter;
import org.interpss.dstab.control.exc.psse.ac2c.Ac2cData;
import org.interpss.dstab.control.exc.psse.ac2c.Ac2cExciter;
import org.interpss.dstab.control.exc.psse.ac3c.Ac3cData;
import org.interpss.dstab.control.exc.psse.ac3c.Ac3cExciter;
import org.interpss.dstab.control.exc.psse.ac4c.Ac4cData;
import org.interpss.dstab.control.exc.psse.ac4c.Ac4cExciter;
import org.interpss.dstab.control.exc.psse.ac5c.Ac5cData;
import org.interpss.dstab.control.exc.psse.ac5c.Ac5cExciter;
import org.interpss.dstab.control.exc.psse.ac6c.Ac6cData;
import org.interpss.dstab.control.exc.psse.ac6c.Ac6cExciter;
import org.interpss.dstab.control.exc.psse.ac7c.Ac7cData;
import org.interpss.dstab.control.exc.psse.ac7c.Ac7cExciter;
import org.interpss.dstab.control.exc.psse.ac8b.Ac8bData;
import org.interpss.dstab.control.exc.psse.ac8b.Ac8bExciter;
import org.interpss.dstab.control.exc.psse.esac4a.Esac4aData;
import org.interpss.dstab.control.exc.psse.esac4a.Esac4aExciter;
import org.interpss.dstab.control.exc.psse.exac4.Exac4Data;
import org.interpss.dstab.control.exc.psse.exac4.Exac4Exciter;
import org.interpss.dstab.control.exc.psse.dc4b.Dc4bData;
import org.interpss.dstab.control.exc.psse.dc4b.Dc4bExciter;
import org.interpss.dstab.control.exc.psse.dc3a.Dc3aData;
import org.interpss.dstab.control.exc.psse.dc3a.Dc3aExciter;
import org.interpss.dstab.control.exc.psse.st6b.St6bData;
import org.interpss.dstab.control.exc.psse.st6b.St6bExciter;
import org.interpss.dstab.control.exc.psse.st7b.St7bData;
import org.interpss.dstab.control.exc.psse.st7b.St7bExciter;
import org.interpss.dstab.control.exc.psse.esst2a.Esst2aData;
import org.interpss.dstab.control.exc.psse.esst2a.Esst2aExciter;
import org.interpss.dstab.control.exc.psse.exst2.Exst2Data;
import org.interpss.dstab.control.exc.psse.exst2.Exst2Exciter;
import org.interpss.dstab.control.exc.psse.st5b.St5bData;
import org.interpss.dstab.control.exc.psse.st5b.St5bExciter;
import org.interpss.dstab.control.exc.psse.rexsys.RexsysData;
import org.interpss.dstab.control.exc.psse.rexsys.RexsysExciter;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciterData;
import org.interpss.dstab.control.exc.simple.SimpleExciter;
import org.interpss.dstab.control.exc.psse.scrx.ScrxData;
import org.interpss.dstab.control.exc.psse.scrx.ScrxExciter;
import org.interpss.dstab.control.exc.psse.esac5a.Esac5aData;
import org.interpss.dstab.control.exc.psse.esac5a.Esac5aExciter;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Data;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.exc.psse.esurry.EsurryData;
import org.interpss.dstab.control.exc.psse.esurry.EsurryExciter;
import org.interpss.dstab.control.exc.psse.exac1a.Exac1aData;
import org.interpss.dstab.control.exc.psse.exac1a.Exac1aExciter;
import org.interpss.dstab.control.exc.psse.exac2.Exac2Data;
import org.interpss.dstab.control.exc.psse.exac2.Exac2Exciter;
import org.interpss.dstab.control.exc.psse.esac1a.Esac1aData;
import org.interpss.dstab.control.exc.psse.esac1a.Esac1aExciter;
import org.interpss.dstab.control.exc.psse.esac2a.Esac2aData;
import org.interpss.dstab.control.exc.psse.esac2a.Esac2aExciter;
import org.interpss.dstab.control.exc.psse.esac6a.Esac6aData;
import org.interpss.dstab.control.exc.psse.esac6a.Esac6aExciter;
import org.interpss.dstab.control.gov.GovernorObjectFactory;
import org.interpss.dstab.control.gov.ieee.hydro1981Type3.Ieee1981Type3HydroGovernor;
import org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor;
import org.interpss.dstab.control.gov.psse.gast.PsseGASTGasTurGovernor;
import org.interpss.dstab.control.gov.psse.degov1.PsseDegov1dGovernor;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1GovernorData;
import org.interpss.dstab.control.gov.psse.h6e.PsseH6eGovernor;
import org.interpss.dstab.control.gov.psse.h6e.PsseH6eGovernorData;
import org.interpss.dstab.control.gov.psse.hyg3.PsseHyg3Governor;
import org.interpss.dstab.control.gov.psse.hyg3.PsseHyg3GovernorData;
import org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernor;
import org.interpss.dstab.control.gov.psse.hygov2.PsseHygov2dGovernor;
import org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernorData;
import org.interpss.dstab.control.gov.psse.hygovr.PsseHygovrGovernor;
import org.interpss.dstab.control.gov.psse.hygovr.PsseHygovrGovernorData;
import org.interpss.dstab.control.gov.psse.lcfb1.Lcfb1Data;
import org.interpss.dstab.control.gov.psse.lcfb1.Lcfb1PrefController;
import org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor;
import org.interpss.dstab.control.gov.psse.pidgov.PssePidgovdGovernor;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.dstab.control.gov.psse.tgov3.PsseTgov3dGovernor;
import org.interpss.dstab.control.gov.psse.wesgov.PsseWesgovdGovernor;
import org.interpss.dstab.control.gov.psse.wpidhy.PsseWpidhydGovernor;
import org.interpss.dstab.control.gov.psse.gastwd.PsseGastwddGovernor;
import org.interpss.dstab.control.gov.psse.gastwd.PsseGastwddGovernorData;
import org.interpss.dstab.control.gov.psse.gast2a.PsseGast2adGovernor;
import org.interpss.dstab.control.gov.psse.gast2a.PsseGast2adGovernorData;
import org.interpss.dstab.control.gov.simple.SimpleGovernor;
import org.interpss.dstab.control.pss.StabilizerObjectFactory;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer;
import org.interpss.dstab.control.pss.ieee.y1992.pss2b.Ieee1992PSS2BStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss2c.Ieee2016PSS2CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss3c.Ieee2016PSS3CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss3c.Ieee2016PSS3CStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2016.pss4c.Ieee2016PSS4CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss4c.Ieee2016PSS4CStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2016.pss5c.Ieee2016PSS5CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss5c.Ieee2016PSS5CStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2016.pss6c.Ieee2016PSS6CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss6c.Ieee2016PSS6CStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2016.pss7c.Ieee2016PSS7CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss7c.Ieee2016PSS7CStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2005.pss3b.Ieee2005PSS3BStabilizer;
import org.interpss.dstab.control.pss.ieee.y2005.pss3b.Ieee2005PSS3BStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizer;
import org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizerData;
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
import org.interpss.dstab.renewable.Wtdta1Data;
import org.interpss.dstab.renewable.Wtdta1Model;
import org.interpss.dstab.renewable.WtgtAData;
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
     * PSS2A IEEE dual-input stabilizer with all six documented signal
     * selectors and validated remote-bus measurements for bus-based signals.
     */
    public Ieee1992PSS2AStabilizer addPss2a(String busId, String genId,
            int ics1, int remoteBus1, int ics2, int remoteBus2, int m, int n,
            double tw1, double tw2, double t6, double tw3, double tw4, double t7,
            double ks2, double ks3, double t8, double t9, double ks1,
            double t1, double t2, double t3, double t4, double vstmax, double vstmin) {
		return addPss2a(busId, genId, ics1, remoteBus1, ics2, remoteBus2, m, n,
				tw1, tw2, t6, tw3, tw4, t7, ks2, ks3, t8, t9, ks1,
				t1, t2, t3, t4, vstmax, vstmin, 1.0, 0.0, 0.0, 1.0);
	}

    public Ieee1992PSS2AStabilizer addPss2a(String busId, String genId,
            int ics1, int remoteBus1, int ics2, int remoteBus2, int m, int n,
            double tw1, double tw2, double t6, double tw3, double tw4, double t7,
            double ks2, double ks3, double t8, double t9, double ks1,
            double t1, double t2, double t3, double t4, double vstmax, double vstmin,
            double a, double ta, double tb, double ks4) {
        Machine machine = findMachine(busId, genId);
        if (machine == null) {
            log.warn("Machine not found for PSS2A: {} {}", busId, genId);
            return null;
        }
        if (ics1 < 1 || ics1 > 6 || ics2 < 1 || ics2 > 6) {
            log.warn("PSS2A selector combination is not implemented at {} {}: "
                    + "ICS1={}, REMBUS1={}, ICS2={}, REMBUS2={}",
                    busId, genId, ics1, remoteBus1, ics2, remoteBus2);
            return null;
        }
		BaseDStabBus<?, ?> localBus = machine.getDStabBus();
		BaseDStabBus<?, ?> input1Bus = resolvePss2aSignalBus(localBus, ics1, remoteBus1);
		BaseDStabBus<?, ?> input2Bus = resolvePss2aSignalBus(localBus, ics2, remoteBus2);
		if (input1Bus == null || input2Bus == null) {
			log.warn("PSS2A remote bus could not be resolved at {} {}: "
					+ "ICS1={}, REMBUS1={}, ICS2={}, REMBUS2={}",
					busId, genId, ics1, remoteBus1, ics2, remoteBus2);
			return null;
		}
		if (tb < 0.0 || (Math.abs(tb) < 1.0e-12 && Math.abs(ta) >= 1.0e-12)) {
			log.warn("PSS2A optional lead-lag is invalid at {} {}: Ta={}, Tb={}",
					busId, genId, ta, tb);
			return null;
		}
		if (t8 < 0.0 || t9 < 0.0
				|| (n > 0 && Math.abs(t9) < 1.0e-12 && Math.abs(t8) >= 1.0e-12)) {
			log.warn("PSS2A ramp-tracking filter is invalid at {} {}: "
					+ "M={}, N={}, T8={}, T9={}", busId, genId, m, n, t8, t9);
			return null;
		}
        Ieee1992PSS2AStabilizer pss = StabilizerObjectFactory
                .createIeee1992PSS2AStabilizer(busId + "-pss2a" + genId, "PSS2A", machine);
		pss.setInputSignalBuses(input1Bus, input2Bus);
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
		data.setA(a);
		data.setTa(ta);
		data.setTb(tb);
		data.setKs4(ks4);
        return pss;
    }

    /** Build the complete PSS/E/PowerWorld PSS2B model. */
    public Ieee1992PSS2BStabilizer addPss2b(String busId, String genId,
            int ics1, int ics2, int m, int n,
            double tw1, double tw2, double t6, double tw3, double tw4, double t7,
            double ks2, double ks3, double t8, double t9, double ks1,
            double t1, double t2, double t3, double t4, double t10, double t11,
            double vsi1max, double vsi1min, double vsi2max, double vsi2min,
            double vstmax, double vstmin, double a, double ta, double tb, double ks4) {
        Machine machine = findMachine(busId, genId);
        if (machine == null) {
            log.warn("Machine not found for PSS2B: {} {}", busId, genId);
            return null;
        }
        if (ics1 < 1 || ics1 > 6 || ics2 < 1 || ics2 > 6 || m < 0 || n < 0) {
            log.warn("Invalid PSS2B selectors/filter orders at {} {}: ICS1={}, ICS2={}, M={}, N={}",
                    busId, genId, ics1, ics2, m, n);
            return null;
        }
        if (tw1 < 0.0 || tw2 < 0.0 || t6 < 0.0 || tw3 < 0.0 || tw4 < 0.0
                || t7 < 0.0 || t8 < 0.0 || t9 < 0.0 || t2 < 0.0 || t4 < 0.0
                || t11 < 0.0 || tb < 0.0
                || (n > 0 && t9 == 0.0 && t8 != 0.0)
                || (tb == 0.0 && ta != 0.0)) {
            log.warn("Invalid PSS2B time constants at {} {}", busId, genId);
            return null;
        }
        Ieee1992PSS2BStabilizer pss = StabilizerObjectFactory
                .createIeee1992PSS2BStabilizer(busId + "-pss2b" + genId, "PSS2B", machine);
        var data = pss.getData();
        data.setIcs1(ics1); data.setIcs2(ics2); data.setM(m); data.setN(n);
        data.setTw1(tw1); data.setTw2(tw2); data.setT6(t6);
        data.setTw3(tw3); data.setTw4(tw4); data.setT7(t7);
        data.setKs2(ks2); data.setKs3(ks3); data.setT8(t8); data.setT9(t9);
        data.setKs1(ks1); data.setT1(t1); data.setT2(t2);
        data.setT3(t3); data.setT4(t4); data.setT10(t10); data.setT11(t11);
        data.setVsi1max(vsi1max); data.setVsi1min(vsi1min);
        data.setVsi2max(vsi2max); data.setVsi2min(vsi2min);
        data.setVstmax(vstmax); data.setVstmin(vstmin);
        data.setA(a); data.setTa(ta); data.setTb(tb); data.setKs4(ks4);
        return pss;
    }

    /** Build the complete IEEE 421.5-2016 PSS2C model. */
    public Ieee2016PSS2CStabilizer addPss2c(String busId, String genId,
            int ics1, int remoteBus1, int ics2, int remoteBus2, int m, int n,
            double tw1, double tw2, double t6, double tw3, double tw4, double t7,
            double ks2, double ks3, double t8, double t9, double ks1,
            double t1, double t2, double t3, double t4, double t10, double t11,
            double vsi1max, double vsi1min, double vsi2max, double vsi2min,
            double vstmax, double vstmin, double t12, double t13,
            double pssActivation, double pssDeactivation,
            double tpgfilt, double xcomp, double tcomp) {
        Machine machine = findMachine(busId, genId);
        if (machine == null) {
            log.warn("Machine not found for PSS2C: {} {}", busId, genId);
            return null;
        }
        if (ics1 < 1 || ics1 > 7 || ics2 < 1 || ics2 > 6 || m < 0 || n < 0) {
            log.warn("Invalid PSS2C selectors/filter orders at {} {}: "
                    + "ICS1={}, ICS2={}, M={}, N={}", busId, genId, ics1, ics2, m, n);
            return null;
        }
        BaseDStabBus<?, ?> localBus = machine.getDStabBus();
        BaseDStabBus<?, ?> input1Bus = ics1 == 7 ? localBus
                : resolvePss2aSignalBus(localBus, ics1, remoteBus1);
        BaseDStabBus<?, ?> input2Bus = resolvePss2aSignalBus(localBus, ics2, remoteBus2);
        if (input1Bus == null || input2Bus == null) {
            log.warn("PSS2C remote bus could not be resolved at {} {}: "
                    + "ICS1={}, REMBUS1={}, ICS2={}, REMBUS2={}",
                    busId, genId, ics1, remoteBus1, ics2, remoteBus2);
            return null;
        }
        if (tw1 < 0.0 || tw2 < 0.0 || t6 < 0.0 || tw3 < 0.0 || tw4 < 0.0
                || t7 < 0.0 || t8 < 0.0 || t9 < 0.0 || t2 < 0.0 || t4 < 0.0
                || t11 < 0.0 || t13 < 0.0 || tpgfilt < 0.0 || tcomp < 0.0
                || (n > 0 && t9 == 0.0 && t8 != 0.0)
                || pssActivation < pssDeactivation) {
            log.warn("Invalid PSS2C time constants or activation thresholds at {} {}",
                    busId, genId);
            return null;
        }
        Ieee2016PSS2CStabilizer pss = StabilizerObjectFactory
                .createIeee2016PSS2CStabilizer(busId + "-pss2c" + genId, "PSS2C", machine);
        pss.setInputSignalBuses(input1Bus, input2Bus);
        var data = pss.getData();
        data.setIcs1(ics1); data.setRemoteBus1(remoteBus1);
        data.setIcs2(ics2); data.setRemoteBus2(remoteBus2);
        data.setM(m); data.setN(n);
        data.setTw1(tw1); data.setTw2(tw2); data.setT6(t6);
        data.setTw3(tw3); data.setTw4(tw4); data.setT7(t7);
        data.setKs2(ks2); data.setKs3(ks3); data.setT8(t8); data.setT9(t9);
        data.setKs1(ks1); data.setT1(t1); data.setT2(t2);
        data.setT3(t3); data.setT4(t4); data.setT10(t10); data.setT11(t11);
        data.setVsi1max(vsi1max); data.setVsi1min(vsi1min);
        data.setVsi2max(vsi2max); data.setVsi2min(vsi2min);
        data.setVstmax(vstmax); data.setVstmin(vstmin);
        data.setT12(t12); data.setT13(t13);
        data.setPssActivation(pssActivation); data.setPssDeactivation(pssDeactivation);
        data.setTpgfilt(tpgfilt); data.setXcomp(xcomp); data.setTcomp(tcomp);
        return pss;
    }

    /** Build the complete IEEE 421.5-2005 PSS3B model. */
    public Ieee2005PSS3BStabilizer addPss3b(String busId, String genId,
            int ics1, int ics2,
            double ks1, double t1, double tw1,
            double ks2, double t2, double tw2, double tw3,
            double a1, double a2, double a3, double a4,
            double a5, double a6, double a7, double a8,
            double vstmax, double vstmin) {
        Machine machine = findMachine(busId, genId);
        if (machine == null) {
            log.warn("Machine not found for PSS3B: {} {}", busId, genId);
            return null;
        }
        if (ics1 < 1 || ics1 > 6 || ics2 < 1 || ics2 > 6) {
            log.warn("Invalid PSS3B input selectors at {} {}: ICS1={}, ICS2={}",
                    busId, genId, ics1, ics2);
            return null;
        }
        // Nonpositive washout constants are a model-defined bypass (Dynawo's
        // validated IEEE PSS3B case uses Tw3=-1). The notch blocks likewise
        // apply their specified highest-denominator-coefficient bypass rule,
        // so only negative transducer time constants are rejected here.
        if (t1 < 0.0 || t2 < 0.0) {
            log.warn("Invalid PSS3B transducer time constants at {} {}", busId, genId);
            return null;
        }
        var data = new Ieee2005PSS3BStabilizerData(ics1, ics2,
                ks1, t1, tw1, ks2, t2, tw2, tw3,
                a1, a2, a3, a4, a5, a6, a7, a8, vstmax, vstmin);
        return StabilizerObjectFactory.createIeee2005PSS3BStabilizer(
                busId + "-pss3b" + genId, data, machine);
    }

    /** Build the complete IEEE 421.5-2005 PSS4B model from its 75 parameters. */
    public Ieee2005PSS4BStabilizer addPss4b(String busId, String genId,
            double[] parameters) {
        Machine machine = findMachine(busId, genId);
        if (machine == null) {
            log.warn("Machine not found for PSS4B: {} {}", busId, genId);
            return null;
        }
        final Ieee2005PSS4BStabilizerData data;
        try {
            data = Ieee2005PSS4BStabilizerData.fromParameters(parameters);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid PSS4B record at {} {}: {}", busId, genId, e.getMessage());
            return null;
        }
        return StabilizerObjectFactory.createIeee2005PSS4BStabilizer(
                busId + "-pss4b" + genId, data, machine);
    }

    /** Build the complete IEEE 421.5-2016 PSS3C model from its 24 parameters. */
    public Ieee2016PSS3CStabilizer addPss3c(String busId, String genId,
            double[] parameters) {
        Machine machine = findMachine(busId, genId);
        if (machine == null) {
            log.warn("Machine not found for PSS3C: {} {}", busId, genId);
            return null;
        }
        final Ieee2016PSS3CStabilizerData data;
        try {
            data = Ieee2016PSS3CStabilizerData.fromParameters(parameters);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid PSS3C record at {} {}: {}", busId, genId, e.getMessage());
            return null;
        }
        if (data.ics1() < 1 || data.ics1() > 7 || data.ics2() < 1 || data.ics2() > 6) {
            log.warn("Invalid PSS3C input selectors at {} {}: ICS1={}, ICS2={}",
                    busId, genId, data.ics1(), data.ics2());
            return null;
        }
        return StabilizerObjectFactory.createIeee2016PSS3CStabilizer(
                busId + "-pss3c" + genId, data, machine);
    }

    /** Build PSS4C from the 94-value PowerWorld/IEEE parameter order. */
    public Ieee2016PSS4CStabilizer addPss4c(String busId, String genId,
            double[] parameters) {
        Machine machine = findMachine(busId, genId);
        if (machine == null) {
            log.warn("Machine not found for PSS4C: {} {}", busId, genId);
            return null;
        }
        final Ieee2016PSS4CStabilizerData data;
        try {
            data = Ieee2016PSS4CStabilizerData.fromPowerWorldParameters(parameters);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid PSS4C record at {} {}: {}", busId, genId, e.getMessage());
            return null;
        }
        return StabilizerObjectFactory.createIeee2016PSS4CStabilizer(
                busId + "-pss4c" + genId, data, machine);
    }

    /** Build PSS5C from the 21-value PowerWorld/IEEE parameter order. */
    public Ieee2016PSS5CStabilizer addPss5c(String busId, String genId,
            double[] parameters) {
        Machine machine = findMachine(busId, genId);
        if (machine == null) {
            log.warn("Machine not found for PSS5C: {} {}", busId, genId);
            return null;
        }
        final Ieee2016PSS5CStabilizerData data;
        try {
            data = Ieee2016PSS5CStabilizerData.fromPowerWorldParameters(parameters);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid PSS5C record at {} {}: {}", busId, genId, e.getMessage());
            return null;
        }
        return StabilizerObjectFactory.createIeee2016PSS5CStabilizer(
                busId + "-pss5c" + genId, data, machine);
    }

    /** Build PSS6C from its 34/35-value PSS/E/PowerWorld parameter order. */
    public Ieee2016PSS6CStabilizer addPss6c(String busId, String genId,
            double[] parameters) {
        Machine machine = findMachine(busId, genId);
        if (machine == null) {
            log.warn("Machine not found for PSS6C: {} {}", busId, genId);
            return null;
        }
        final Ieee2016PSS6CStabilizerData data;
        try {
            data = Ieee2016PSS6CStabilizerData.fromPsseParameters(parameters);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid PSS6C record at {} {}: {}", busId, genId, e.getMessage());
            return null;
        }
        if (data.ics1() < 1 || data.ics1() > 7 || data.ics2() < 1 || data.ics2() > 6
                || data.t1() < 0.0 || data.t2() < 0.0 || data.t3() < 0.0
                || data.t4() < 0.0 || data.td() < 0.0 || data.ti1() < 0.0
                || data.ti2() < 0.0 || data.ti3() < 0.0 || data.ti4() < 0.0
                || data.tpgfilt() < 0.0 || data.tcomp() < 0.0
                || data.pssActivation() < data.pssDeactivation()) {
            log.warn("Invalid PSS6C selectors, time constants, or thresholds at {} {}",
                    busId, genId);
            return null;
        }
        BaseDStabBus<?, ?> localBus = machine.getDStabBus();
        BaseDStabBus<?, ?> input1Bus = data.ics1() == 7 ? localBus
                : resolvePss2aSignalBus(localBus, data.ics1(), data.remoteBus1());
        BaseDStabBus<?, ?> input2Bus = resolvePss2aSignalBus(
                localBus, data.ics2(), data.remoteBus2());
        if (input1Bus == null || input2Bus == null) {
            log.warn("PSS6C remote bus could not be resolved at {} {}", busId, genId);
            return null;
        }
        Ieee2016PSS6CStabilizer pss = StabilizerObjectFactory
                .createIeee2016PSS6CStabilizer(busId + "-pss6c" + genId, data, machine);
        pss.setInputSignalBuses(input1Bus, input2Bus);
        return pss;
    }

    /** Build PSS7C from its 38/39-value PSS/E/PowerWorld parameter order. */
    public Ieee2016PSS7CStabilizer addPss7c(String busId, String genId,
            double[] parameters) {
        Machine machine = findMachine(busId, genId);
        if (machine == null) {
            log.warn("Machine not found for PSS7C: {} {}", busId, genId);
            return null;
        }
        final Ieee2016PSS7CStabilizerData data;
        try {
            data = Ieee2016PSS7CStabilizerData.fromPsseParameters(parameters);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid PSS7C record at {} {}: {}", busId, genId, e.getMessage());
            return null;
        }
        if (data.ics1() < 1 || data.ics1() > 7 || data.ics2() < 1 || data.ics2() > 6
                || data.m() < 0 || data.n() < 0 || (data.n() > 0 && data.m() == 0)
                || data.t6() < 0.0 || data.t7() < 0.0 || data.t8() < 0.0
                || data.t9() < 0.0 || data.tpgfilt() < 0.0 || data.tcomp() < 0.0
                || data.pssActivation() < data.pssDeactivation()) {
            log.warn("Invalid PSS7C selectors, orders, time constants, or thresholds at {} {}",
                    busId, genId);
            return null;
        }
        BaseDStabBus<?, ?> localBus = machine.getDStabBus();
        BaseDStabBus<?, ?> input1Bus = data.ics1() == 7 ? localBus
                : resolvePss2aSignalBus(localBus, data.ics1(), data.remoteBus1());
        BaseDStabBus<?, ?> input2Bus = resolvePss2aSignalBus(
                localBus, data.ics2(), data.remoteBus2());
        if (input1Bus == null || input2Bus == null) {
            log.warn("PSS7C remote bus could not be resolved at {} {}", busId, genId);
            return null;
        }
        Ieee2016PSS7CStabilizer pss = StabilizerObjectFactory
                .createIeee2016PSS7CStabilizer(busId + "-pss7c" + genId, data, machine);
        pss.setInputSignalBuses(input1Bus, input2Bus);
        return pss;
    }

	private BaseDStabBus<?, ?> resolvePss2aSignalBus(BaseDStabBus<?, ?> localBus,
			int inputCode, int remoteBusNumber) {
		// Rotor speed, generator electrical power, and accelerating power are
		// unit signals. PSS/E ignores REMBUS for these selector codes.
		if (inputCode == 1 || inputCode == 3 || inputCode == 4 || remoteBusNumber == 0) {
			return localBus;
		}
		return network.getDStabBus("Bus" + remoteBusNumber);
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
        return addExcIeeet1(busId, genId, tr, ka, ta, vrmax, vrmin,
                ke, te, kf, tf, e1, se1, e2, se2, 0.0);
    }

    /** PSS/E IEEET1 including the optional speed-multiplier field. */
    public Ieee1968Type1Exciter addExcIeeet1(String busId, String genId,
            double tr, double ka, double ta, double vrmax, double vrmin,
            double ke, double te, double kf, double tf,
            double e1, double se1, double e2, double se2, double spdmlt) {
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
        exc.getData().setSpdmlt(spdmlt);
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
     * Parameters: TR, KA, TA, TC, TB, VRMAX, VRMIN, KF, TF, KC, VIMAX, VIMIN
     *
     * @return the created exciter, or null if the machine was not found
     */
    public IEEE1981ST1Exciter addExcIeee1981St1(String busId, String genId,
            double ka, double ta, double tc, double tb,
            double vrmax, double vrmin,
            double kf, double tf, double kc,
            double vimax, double vimin) {
        return addExcIeee1981St1(busId, genId, 0.02, ka, ta, tc, tb,
                vrmax, vrmin, kf, tf, kc, vimax, vimin);
    }

    /**
     * IEEE 1981 ST1 exciter with an explicit terminal-voltage transducer time
     * constant. This overload is used by the PSS/E EXST1 exchange model.
     */
    public IEEE1981ST1Exciter addExcIeee1981St1(String busId, String genId,
            double tr, double ka, double ta, double tc, double tb,
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
        exc.getData().setTr(tr);
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

    /** PSS/E IEEEX1 / WECC EXDC1 excitation system. */
    public IEEE1981DC1Exciter addExcIeeex1(String busId, String genId,
            double tr, double ka, double ta, double tb, double tc,
            double vrmax, double vrmin, double ke, double te,
            double kf, double tf, double e1, double se1, double e2, double se2) {
        return addExcIeeex1(busId, genId, tr, ka, ta, tb, tc, vrmax, vrmin,
                ke, te, kf, tf, 0.0, e1, se1, e2, se2);
    }

    public Ieeex1Exciter addExcIeeex1(String busId, String genId,
            double tr, double ka, double ta, double tb, double tc,
            double vrmax, double vrmin, double ke, double te,
            double kf, double tf, double switchValue,
            double e1, double se1, double e2, double se2) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for IEEEX1 exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        Ieeex1Exciter exc = ExciterObjectFactory.createIeeex1Exciter(
                mach.getId() + "_Exc", mach);
        exc.setTransducerTimeConstant(tr);
        exc.setSwitchValue(switchValue);
        exc.getData().setKa(ka);
        exc.getData().setTa(ta);
        exc.getData().setTb(tb);
        exc.getData().setTc(tc);
        exc.getData().setVrmax(vrmax);
        exc.getData().setVrmin(vrmin);
        exc.getData().setKe(ke);
        exc.getData().setTe(te);
        exc.getData().setKf(kf);
        exc.getData().setTf(tf);
        exc.getData().setE1(e1);
        exc.getData().setSe_e1(se1);
        exc.getData().setE2(e2);
        exc.getData().setSe_e2(se2);
        return exc;
    }

    /** PSS/E EXDC2 / PowerWorld EXDC2_PTI excitation system. */
    public Exdc2Exciter addExcExdc2(String busId, String genId,
            double tr, double ka, double ta, double tb, double tc,
            double vrmax, double vrmin, double ke, double te,
            double kf, double tf, double switchValue,
            double e1, double se1, double e2, double se2) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for EXDC2 exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        Exdc2Exciter exc = ExciterObjectFactory.createExdc2Exciter(
                mach.getId() + "_Exc", mach);
        exc.setTransducerTimeConstant(tr);
        exc.setSwitchValue(switchValue);
        exc.getData().setKa(ka);
        exc.getData().setTa(ta);
        exc.getData().setTb(tb);
        exc.getData().setTc(tc);
        exc.getData().setVrmax(vrmax);
        exc.getData().setVrmin(vrmin);
        exc.getData().setKe(ke);
        exc.getData().setTe(te);
        exc.getData().setKf(kf);
        exc.getData().setTf(tf);
        exc.getData().setE1(e1);
        exc.getData().setSe_e1(se1);
        exc.getData().setE2(e2);
        exc.getData().setSe_e2(se2);
        return exc;
    }

    /** PSLF/PowerWorld EXDC2A with a second rate-feedback time constant. */
    public Exdc2aExciter addExcExdc2a(String busId, String genId,
            double tr, double ka, double ta, double tb, double tc,
            double vrmax, double vrmin, double ke, double te,
            double kf, double tf1, double tf2,
            double e1, double se1, double e2, double se2) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for EXDC2A exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        Exdc2aExciter exc = ExciterObjectFactory.createExdc2aExciter(
                mach.getId() + "_Exc", mach);
        exc.setTransducerTimeConstant(tr);
        exc.setTf2(tf2);
        exc.getData().setKa(ka);
        exc.getData().setTa(ta);
        exc.getData().setTb(tb);
        exc.getData().setTc(tc);
        exc.getData().setVrmax(vrmax);
        exc.getData().setVrmin(vrmin);
        exc.getData().setKe(ke);
        exc.getData().setTe(te);
        exc.getData().setKf(kf);
        exc.getData().setTf(tf1);
        exc.getData().setE1(e1);
        exc.getData().setSe_e1(se1);
        exc.getData().setE2(e2);
        exc.getData().setSe_e2(se2);
        return exc;
    }

    /** IEEE 421.5-2005 / PSS/E AC8B rotating excitation system. */
    public Ac8bExciter addExcAc8b(String busId, String genId, Ac8bData data) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for AC8B exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        return ExciterObjectFactory.createAc8bExciter(mach.getId() + "_Exc", data, mach);
    }

    /** PSS/E ESAC4A / IEEE Type AC4A excitation system. */
    public Esac4aExciter addExcEsac4a(String busId, String genId, Esac4aData data) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for ESAC4A exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        return ExciterObjectFactory.createEsac4aExciter(mach.getId() + "_Exc", data, mach);
    }

    /** PSS/E EXAC4 / IEEE Type AC4 excitation system. */
    public Exac4Exciter addExcExac4(String busId, String genId, Exac4Data data) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for EXAC4 exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        return ExciterObjectFactory.createExac4Exciter(mach.getId() + "_Exc", data, mach);
    }

    /** IEEE 421.5 DC4B / PSS/E ESDC4B excitation system. */
    public Dc4bExciter addExcDc4b(String busId,String genId,String modelName,Dc4bData data) {
        Machine mach=findMachine(busId,genId);
        if(mach==null){log.warn("Machine not found for {} exciter: bus={}, gen={}",modelName,busId,genId);return null;}
        return ExciterObjectFactory.createDc4bExciter(mach.getId()+"_Exc",modelName,data,mach);
    }

    /** IEEE 421.5-2005 DC3A / PSLF ESDC3A rheostatic excitation system. */
    public Dc3aExciter addExcDc3a(String busId, String genId, String modelName,
            Dc3aData data) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for {} exciter: bus={}, gen={}", modelName, busId, genId);
            return null;
        }
        return ExciterObjectFactory.createDc3aExciter(
                mach.getId() + "_Exc", modelName, data, mach);
    }

    /** IEEE ST6B / PSLF ESST6B static excitation system. */
    public St6bExciter addExcSt6b(String busId,String genId,String modelName,St6bData data){
        Machine mach=findMachine(busId,genId);if(mach==null){log.warn("Machine not found for {} exciter: bus={}, gen={}",modelName,busId,genId);return null;}
        return ExciterObjectFactory.createSt6bExciter(mach.getId()+"_Exc",modelName,data,mach);
    }

    /** IEEE 421.5-2005 ST7B / PSLF ESST7B excitation system. */
    public St7bExciter addExcSt7b(String busId,String genId,String modelName,St7bData data){
        Machine mach=findMachine(busId,genId);if(mach==null){log.warn("Machine not found for {} exciter: bus={}, gen={}",modelName,busId,genId);return null;}
        return ExciterObjectFactory.createSt7bExciter(mach.getId()+"_Exc",modelName,data,mach);
    }

    /** IEEE 421.5-2005 / PSS/E ESST2A compound-source excitation system. */
    public Esst2aExciter addExcEsst2a(String busId, String genId, Esst2aData data) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for ESST2A exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        return ExciterObjectFactory.createEsst2aExciter(mach.getId() + "_Exc", data, mach);
    }

    /** PSS/E EXST2 additive compound-source excitation system. */
    public Exst2Exciter addExcExst2(String busId, String genId, Exst2Data data) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for EXST2 exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        return ExciterObjectFactory.createExst2Exciter(mach.getId() + "_Exc", data, mach);
    }

    /** IEEE 421.5-2005 ST5B / PSLF ESST5B static excitation system. */
    public St5bExciter addExcSt5b(String busId, String genId, String modelName,
            St5bData data) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for {} exciter: bus={}, gen={}", modelName, busId, genId);
            return null;
        }
        return ExciterObjectFactory.createSt5bExciter(
                mach.getId() + "_Exc", modelName, data, mach);
    }

    /** IEEE 421.5-2005 AC7B / PSS/E ESAC7B rotating excitation system. */
    public Ac7bExciter addExcAc7b(String busId, String genId, String modelName,
            Ac7bData data) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for {} exciter: bus={}, gen={}", modelName, busId, genId);
            return null;
        }
        return ExciterObjectFactory.createAc7bExciter(
                mach.getId() + "_Exc", modelName, data, mach);
    }

    /** PSS/E REXSYS general-purpose rotating excitation system. */
    public RexsysExciter addExcRexsys(String busId, String genId, RexsysData data) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for REXSYS exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        return ExciterObjectFactory.createRexsysExciter(mach.getId() + "_Exc", data, mach);
    }

    /** PSS/E IEEET4 / WECC EXDC4 IEEE Type 4 excitation system. */
    public Ieeet4Exciter addExcIeeet4(String busId, String genId, String modelName,
            double kr, double trh, double kv, double vrmax, double vrmin,
            double te, double ke, double e1, double se1, double e2, double se2) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for {} exciter: bus={}, gen={}", modelName, busId, genId);
            return null;
        }
        Ieeet4Data data = new Ieeet4Data();
        data.setKr(kr); data.setTrh(trh); data.setKv(kv);
        data.setVrmax(vrmax); data.setVrmin(vrmin); data.setTe(te); data.setKe(ke);
        data.setE1(e1); data.setSe1(se1); data.setE2(e2); data.setSe2(se2);
        return ExciterObjectFactory.createIeeet4Exciter(
                mach.getId() + "_Exc", modelName, data, mach);
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
        return addExcEsdc2a(busId, genId, tr, ka, ta, tc, tb, vrmax, vrmin,
                ke, te, kf, tf, 0.0, e1, se1, e2, se2);
    }

    /** PSS/E ESDC2A exciter including the output speed-multiplier flag. */
    public org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aExciter addExcEsdc2a(
            String busId, String genId,
            double tr, double ka, double ta, double tc, double tb,
            double vrmax, double vrmin, double ke, double te,
            double kf, double tf, double spdmlt,
            double e1, double se1, double e2, double se2) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) return null;
        var data = new org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aData();
        data.setTr(tr); data.setKa(ka); data.setTa(ta); data.setTc(tc); data.setTb(tb);
        data.setVrmax(vrmax); data.setVrmin(vrmin); data.setKe(ke); data.setTe(te);
        data.setKf(kf); data.setTf(tf); data.setSpdmlt(spdmlt);
        data.setE1(e1); data.setSe1(se1);
        data.setE2(e2); data.setSe2(se2);
        var exc = new org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aExciter(
                mach.getId() + "_Exc", data, mach);
        return exc;
    }

    /** PSS/E ESDC1A reuses the DC model chain with constant regulator limits. */
    public org.interpss.dstab.control.exc.psse.esdc1a.Esdc1aExciter addExcEsdc1a(
            String busId, String genId,
            double tr, double ka, double ta, double tc, double tb,
            double vrmax, double vrmin, double ke, double te,
            double kf, double tf, double spdmlt,
            double e1, double se1, double e2, double se2) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) return null;
        var data = new org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aData();
        data.setTr(tr); data.setKa(ka); data.setTa(ta); data.setTc(tc); data.setTb(tb);
        data.setVrmax(vrmax); data.setVrmin(vrmin); data.setKe(ke); data.setTe(te);
        data.setKf(kf); data.setTf(tf); data.setSpdmlt(spdmlt);
        data.setE1(e1); data.setSe1(se1); data.setE2(e2); data.setSe2(se2);
        return new org.interpss.dstab.control.exc.psse.esdc1a.Esdc1aExciter(
                mach.getId() + "_Exc", data, mach);
    }

    /** Attach the PSS/E/IEEE ESAC5A simplified rotating AC exciter. */
    public Esac5aExciter addExcEsac5a(String busId, String genId, Esac5aData data) {
        if (data == null || data.getKa() <= 0.0 || data.getTe() <= 0.0
                || data.getTr() < 0.0 || data.getTa() < 0.0
                || data.getTf1() < 0.0 || data.getTf2() < 0.0 || data.getTf3() < 0.0) {
            log.warn("Invalid ESAC5A parameters at bus={}, gen={}", busId, genId);
            return null;
        }
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for ESAC5A exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        return new Esac5aExciter(mach.getId() + "_Exc", data, mach);
    }

    /** Attach the PSS/E EXAC1 rotating AC exciter. */
    public Exac1Exciter addExcExac1(String busId, String genId, Exac1Data data) {
        if (data == null || data.getKa() <= 0.0 || data.getTe() <= 0.0 || data.getTf() <= 0.0
                || data.getTr() < 0.0 || data.getTb() < 0.0 || data.getTa() < 0.0
                || data.getKc() < 0.0) {
            log.warn("Invalid EXAC1 parameters at bus={}, gen={}", busId, genId);
            return null;
        }
        Machine mach=findMachine(busId,genId);
        if (mach==null) { log.warn("Machine not found for EXAC1: bus={}, gen={}",busId,genId); return null; }
        return new Exac1Exciter(mach.getId()+"_Exc",data,mach);
    }

    /** Attach the PSS/E ESURRY (WECC EXAC1M) excitation system. */
    public EsurryExciter addExcEsurry(String busId,String genId,EsurryData data) {
        if (data==null || data.getK10()<=0.0 || data.getTe()<=0.0 || data.getTf()<=0.0
                || data.getTr()<0.0 || data.getT1()<0.0 || data.getTb()<0.0
                || data.getTd()<0.0 || data.getKc()<0.0) {
            log.warn("Invalid ESURRY parameters at bus={}, gen={}",busId,genId);
            return null;
        }
        Machine mach=findMachine(busId,genId);
        if (mach==null) {
            log.warn("Machine not found for ESURRY: bus={}, gen={}",busId,genId);
            return null;
        }
        return new EsurryExciter(mach.getId()+"_Exc",data,mach);
    }

    /** Attach the PSS/E EXAC1A modified rotating AC exciter. */
    public Exac1aExciter addExcExac1a(String busId,String genId,Exac1aData data) {
        if (data==null || data.getKa()<=0.0 || data.getTe()<=0.0 || data.getTf()<=0.0
                || data.getTr()<0.0 || data.getTb()<0.0 || data.getTa()<0.0
                || data.getKc()<0.0) {
            log.warn("Invalid EXAC1A parameters at bus={}, gen={}",busId,genId);
            return null;
        }
        Machine mach=findMachine(busId,genId);
        if (mach==null) { log.warn("Machine not found for EXAC1A: bus={}, gen={}",busId,genId); return null; }
        return new Exac1aExciter(mach.getId()+"_Exc",data,mach);
    }

    /** Attach the PSS/E EXAC2 rotating AC exciter. */
    public Exac2Exciter addExcExac2(String busId,String genId,Exac2Data data) {
        if(data==null || data.getKa()<=0 || data.getKb()<=0 || data.getKl()<=0
                || data.getTe()<=0 || data.getTf()<=0 || data.getTr()<0
                || data.getTb()<0 || data.getTa()<0 || data.getKc()<0) {
            log.warn("Invalid EXAC2 parameters at bus={}, gen={}",busId,genId);
            return null;
        }
        Machine mach=findMachine(busId,genId);
        if(mach==null){log.warn("Machine not found for EXAC2: bus={}, gen={}",busId,genId);return null;}
        return new Exac2Exciter(mach.getId()+"_Exc",data,mach);
    }

    /** Attach the IEEE 421.5/PSS/E ESAC1A rotating AC exciter. */
    public Esac1aExciter addExcEsac1a(String busId,String genId,Esac1aData data) {
        if (data==null || data.getKa()<=0.0 || data.getTe()<=0.0 || data.getTf()<=0.0
                || data.getTr()<0.0 || data.getTb()<0.0 || data.getTa()<0.0
                || data.getKc()<0.0) {
            log.warn("Invalid ESAC1A parameters at bus={}, gen={}",busId,genId);
            return null;
        }
        Machine mach=findMachine(busId,genId);
        if (mach==null) { log.warn("Machine not found for ESAC1A: bus={}, gen={}",busId,genId); return null; }
        return new Esac1aExciter(mach.getId()+"_Exc",data,mach);
    }

    /** Attach the IEEE 421.5-2016 / PSS/E AC1C rotating AC exciter. */
    public Ac1cExciter addExcAc1c(String busId,String genId,Ac1cData data) {
        if (data==null || data.getTr()<0.0 || data.getTb()<0.0 || data.getTa()<0.0
                || data.getTe()<0.0 || data.getTf()<0.0 || data.getKc()<0.0
                || data.getKd()<0.0 || data.getOelLocation()<0 || data.getOelLocation()>2
                || data.getUelLocation()<0 || data.getUelLocation()>2
                || data.getSclLocation()<0 || data.getSclLocation()>2) {
            log.warn("Invalid AC1C parameters at bus={}, gen={}",busId,genId);
            return null;
        }
        Machine mach=findMachine(busId,genId);
        if (mach==null) { log.warn("Machine not found for AC1C: bus={}, gen={}",busId,genId); return null; }
        return new Ac1cExciter(mach.getId()+"_Exc",data,mach);
    }

    /** Attach the IEEE 421.5-2016 / PSS/E AC2C high-initial-response AC exciter. */
    public Ac2cExciter addExcAc2c(String busId,String genId,Ac2cData data) {
        if (data==null || data.getTr()<0.0 || data.getTb()<0.0 || data.getTa()<0.0
                || data.getTe()<0.0 || data.getTf()<0.0 || data.getKc()<0.0
                || data.getKd()<0.0 || data.getOelLocation()<0 || data.getOelLocation()>2
                || data.getUelLocation()<0 || data.getUelLocation()>2
                || data.getSclLocation()<0 || data.getSclLocation()>2) {
            log.warn("Invalid AC2C parameters at bus={}, gen={}",busId,genId);
            return null;
        }
        Machine mach=findMachine(busId,genId);
        if (mach==null) { log.warn("Machine not found for AC2C: bus={}, gen={}",busId,genId); return null; }
        return new Ac2cExciter(mach.getId()+"_Exc",data,mach);
    }

    /** Attach the IEEE 421.5-2016 / PSS/E AC3C alternator-rectifier exciter. */
    public Ac3cExciter addExcAc3c(String busId,String genId,Ac3cData data) {
        if (data==null || data.getTr()<0.0 || data.getTb()<0.0 || data.getTa()<0.0
                || data.getTe()<0.0 || data.getTf()<0.0 || data.getTdr()<0.0
                || data.getKc()<0.0 || data.getKd()<0.0
                || data.getOelLocation()<0 || data.getOelLocation()>2
                || data.getUelLocation()<0 || data.getUelLocation()>2
                || data.getSclLocation()<0 || data.getSclLocation()>2) {
            log.warn("Invalid AC3C parameters at bus={}, gen={}",busId,genId);
            return null;
        }
        Machine mach=findMachine(busId,genId);
        if (mach==null) { log.warn("Machine not found for AC3C: bus={}, gen={}",busId,genId); return null; }
        return new Ac3cExciter(mach.getId()+"_Exc",data,mach);
    }

    /** Attach the IEEE 421.5-2016 / PSS/E AC4C controlled-rectifier exciter. */
    public Ac4cExciter addExcAc4c(String busId,String genId,Ac4cData data) {
        if(data==null || data.getTr()<0 || data.getTb()<0 || data.getTa()<0
                || data.getKa()<=0 || data.getKc()<0
                || data.getOelLocation()<0 || data.getOelLocation()>2
                || data.getUelLocation()<0 || data.getUelLocation()>2
                || data.getSclLocation()<0 || data.getSclLocation()>2) {
            log.warn("Invalid AC4C parameters at bus={}, gen={}",busId,genId);return null;
        }
        Machine mach=findMachine(busId,genId);
        if(mach==null){log.warn("Machine not found for AC4C: bus={}, gen={}",busId,genId);return null;}
        return new Ac4cExciter(mach.getId()+"_Exc",data,mach);
    }

    /** Attach the IEEE 421.5-2016 / PSS/E AC5C simplified rotating exciter. */
    public Ac5cExciter addExcAc5c(String busId,String genId,Ac5cData data){
        if(data==null||data.getTr()<0||data.getTa()<0||data.getTe()<=0||data.getTf1()<=0
                ||data.getTf2()<0||data.getKc()<0||data.getKd()<0||data.getKa()==0
                ||data.getOelLocation()<0||data.getOelLocation()>2
                ||data.getUelLocation()<0||data.getUelLocation()>2
                ||data.getSclLocation()<0||data.getSclLocation()>2){
            log.warn("Invalid AC5C parameters at bus={}, gen={}",busId,genId);return null;
        }
        Machine mach=findMachine(busId,genId);
        if(mach==null){log.warn("Machine not found for AC5C: bus={}, gen={}",busId,genId);return null;}
        return new Ac5cExciter(mach.getId()+"_Exc",data,mach);
    }

    /** Attach the IEEE 421.5-2016 / PSS/E AC6C alternator-rectifier exciter. */
    public Ac6cExciter addExcAc6c(String busId,String genId,Ac6cData data){
        if(data==null||data.getTr()<0||data.getTa()<0||data.getTk()<0||data.getTb()<0
                ||data.getTc()<0||data.getTe()<=0||data.getTh()<0||data.getTj()<0
                ||data.getKc()<0||data.getKd()<0||data.getVhmax()<0||data.getKa()==0
                ||data.getOelLocation()<0||data.getOelLocation()>2
                ||data.getUelLocation()<0||data.getUelLocation()>2
                ||data.getSclLocation()<0||data.getSclLocation()>2){
            log.warn("Invalid AC6C parameters at bus={}, gen={}",busId,genId);return null;
        }
        Machine mach=findMachine(busId,genId);
        if(mach==null){log.warn("Machine not found for AC6C: bus={}, gen={}",busId,genId);return null;}
        return new Ac6cExciter(mach.getId()+"_Exc",data,mach);
    }

    /** Attach the IEEE 421.5-2016 / PSS/E AC7C alternator-rectifier exciter. */
    public Ac7cExciter addExcAc7c(String busId,String genId,Ac7cData data){
        if(data==null||data.getTr()<0||data.getTdr()<0||data.getTf()<0||data.getTe()<0
                ||data.getKc()<0||data.getKd()<0||data.getKc1()<0||data.getVbmax()<0
                ||data.getOelLocation()<0||data.getOelLocation()>4
                ||data.getUelLocation()<0||data.getUelLocation()>3
                ||data.getSclLocation()<0||data.getSclLocation()>3
                ||data.getVosLocation()<1||data.getVosLocation()>2
                ||data.getSw1()<1||data.getSw1()>2||data.getSw2()<1||data.getSw2()>2){
            log.warn("Invalid AC7C parameters at bus={}, gen={}",busId,genId);return null;
        }
        Machine mach=findMachine(busId,genId);
        if(mach==null){log.warn("Machine not found for AC7C: bus={}, gen={}",busId,genId);return null;}
        return new Ac7cExciter(mach.getId()+"_Exc",data,mach);
    }

    /** Attach the IEEE 421.5/PSS/E ESAC2A rotating AC exciter. */
    public Esac2aExciter addExcEsac2a(String busId,String genId,Esac2aData data) {
        if (data==null || data.getKa()<=0.0 || data.getKb()<=0.0 || data.getTe()<=0.0
                || data.getTf()<=0.0 || data.getTr()<0.0 || data.getTb()<0.0
                || data.getTa()<0.0 || data.getKc()<0.0 || data.getVfemax()<0.0) {
            log.warn("Invalid ESAC2A parameters at bus={}, gen={}",busId,genId);
            return null;
        }
        Machine mach=findMachine(busId,genId);
        if (mach==null){log.warn("Machine not found for ESAC2A: bus={}, gen={}",busId,genId);return null;}
        return new Esac2aExciter(mach.getId()+"_Exc",data,mach);
    }

    /** Attach the IEEE 421.5/PSS/E ESAC6A rotating AC exciter. */
    public Esac6aExciter addExcEsac6a(String busId,String genId,Esac6aData data) {
        if (data==null || data.getKa()<=0.0 || data.getTe()<=0.0
                || data.getTr()<0.0 || data.getTa()<0.0 || data.getTk()<0.0
                || data.getTb()<0.0 || data.getTc()<0.0 || data.getTh()<0.0
                || data.getTj()<0.0 || data.getKc()<0.0 || data.getKd()<0.0
                || data.getVhmax()<0.0) {
            log.warn("Invalid ESAC6A parameters at bus={}, gen={}",busId,genId);
            return null;
        }
        Machine mach=findMachine(busId,genId);
        if (mach==null){log.warn("Machine not found for ESAC6A: bus={}, gen={}",busId,genId);return null;}
        return new Esac6aExciter(mach.getId()+"_Exc",data,mach);
    }

    /** Attach the eight-parameter PSS/E SCRX excitation system. */
    public ScrxExciter addExcScrx(String busId, String genId, ScrxData data) {
        if (data == null || data.getTb() < 0.0 || data.getTe() < 0.0
                || data.getK() <= 0.0 || data.getRcOverRfd() < 0.0
                || (data.getCswitch() != 0 && data.getCswitch() != 1)) {
            log.warn("Invalid SCRX parameters at bus={}, gen={}", busId, genId);
            return null;
        }
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for SCRX exciter: bus={}, gen={}", busId, genId);
            return null;
        }
        return new ScrxExciter(mach.getId() + "_Exc", data, mach);
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

    /** Attach a WECC H6E/PSS/E H6EU1 Kaplan hydro governor. */
    public PsseH6eGovernor addGovH6e(String busId, String genId, PsseH6eGovernorData data) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for H6E governor: bus={}, gen={}", busId, genId);
            return null;
        }
        PsseH6eGovernor gov = new PsseH6eGovernor(mach.getId() + "_Gov", "H6E", "PSS/E");
        copyH6eData(data, gov.getData());
        if (!gov.validateParameters()) {
            log.warn("Invalid H6E parameters: bus={}, gen={}", busId, genId);
            return null;
        }
        gov.setMachine(mach);
        return gov;
    }

    private static void copyH6eData(PsseH6eGovernorData s, PsseH6eGovernorData t) {
        t.setFd(s.getFd()); t.setRe(s.getRe()); t.setRg(s.getRg());
        t.setTpe(s.getTpe()); t.setTsp(s.getTsp()); t.setKp(s.getKp());
        t.setKi(s.getKi()); t.setKd(s.getKd()); t.setTd(s.getTd());
        t.setVelm(s.getVelm()); t.setGmax(s.getGmax()); t.setGmin(s.getGmin());
        t.setBuf(s.getBuf()); t.setBuv(s.getBuv()); t.setKg(s.getKg());
        t.setTg(s.getTg()); t.setBlg(s.getBlg()); t.setDbbd(s.getDbbd());
        t.setTbd(s.getTbd()); t.setBlb(s.getBlb()); t.setDbbs(s.getDbbs());
        t.setTbs(s.getTbs()); t.setBgvmin(s.getBgvmin()); t.setBlv(s.getBlv());
        t.setDturb(s.getDturb()); t.setPgc(s.getPgc()); t.setDeff(s.getDeff());
        t.setHdam(s.getHdam()); t.setTw(s.getTw()); t.setGv(s.getGv());
        t.setPgv(s.getPgv()); t.setBgv(s.getBgv()); t.setSprate(s.getSprate());
        t.setDb1(s.getDb1()); t.setEps(s.getEps()); t.setTrate(s.getTrate());
    }

    /** Attach a WECC HYG3/PSS/E HYG3U1 hydro governor. */
    public PsseHyg3Governor addGovHyg3(String busId, String genId,
            PsseHyg3GovernorData data) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for HYG3 governor: bus={}, gen={}", busId, genId);
            return null;
        }
        PsseHyg3Governor gov = new PsseHyg3Governor(
                mach.getId() + "_Gov", "HYG3", "PSS/E");
        copyHyg3Data(data, gov.getData());
        if (!gov.validateParameters()) {
            log.warn("Invalid HYG3 parameters: bus={}, gen={}", busId, genId);
            return null;
        }
        gov.setMachine(mach);
        return gov;
    }

    private static void copyHyg3Data(PsseHyg3GovernorData source,
            PsseHyg3GovernorData target) {
        target.setControlFlag(source.getControlFlag());
        target.setRgate(source.getRgate()); target.setRelec(source.getRelec());
        target.setTt(source.getTt()); target.setTd(source.getTd());
        target.setK2(source.getK2()); target.setKi(source.getKi());
        target.setK1(source.getK1()); target.setTf(source.getTf());
        target.setKg(source.getKg()); target.setTp(source.getTp());
        target.setVelopen(source.getVelopen()); target.setVelclose(source.getVelclose());
        target.setPmax(source.getPmax()); target.setPmin(source.getPmin());
        target.setDb2(source.getDb2()); target.setGv(source.getGv());
        target.setPgv(source.getPgv()); target.setH0(source.getH0());
        target.setQnl(source.getQnl()); target.setTw(source.getTw());
        target.setAt(source.getAt()); target.setDturb(source.getDturb());
        target.setTrate(source.getTrate()); target.setDbH(source.getDbH());
        target.setEps(source.getEps()); target.setDbL(source.getDbL());
    }

    /** Attach a PSS/E HYGOVR1 fourth-order lead-lag hydro governor. */
    public PsseHygovrGovernor addGovHygovr1(String busId, String genId,
            PsseHygovrGovernorData data) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for HYGOVR1 governor: bus={}, gen={}", busId, genId);
            return null;
        }
        PsseHygovrGovernor gov = GovernorObjectFactory.createPsseHYGOVR1Governor(
                mach.getId() + "_Gov", "HYGOVR1", mach);
        PsseHygovrGovernorData t = gov.getData();
        t.setDb1(data.getDb1()); t.setErr(data.getErr()); t.setTd(data.getTd());
        t.setT1(data.getT1()); t.setT2(data.getT2()); t.setT3(data.getT3());
        t.setT4(data.getT4()); t.setT5(data.getT5()); t.setT6(data.getT6());
        t.setT7(data.getT7()); t.setT8(data.getT8()); t.setKp(data.getKp());
        t.setR(data.getR()); t.setTt(data.getTt()); t.setKg(data.getKg());
        t.setTp(data.getTp()); t.setVelopen(data.getVelopen());
        t.setVelclose(data.getVelclose()); t.setPmax(data.getPmax());
        t.setPmin(data.getPmin()); t.setDb2(data.getDb2()); t.setTw(data.getTw());
        t.setAt(data.getAt()); t.setDturb(data.getDturb()); t.setQnl(data.getQnl());
        t.setTrate(data.getTrate());
        if (!gov.validateParameters()) {
            log.warn("Invalid HYGOVR1 parameters: bus={}, gen={}", busId, genId);
            return null;
        }
        return gov;
    }

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

    /** PSS/E WESGOVD Westinghouse sampled-data gas-turbine governor. */
    public PsseWesgovdGovernor addGovWesgovd(String busId, String genId,
            double deltaTc, double deltaTp, double droop, double kp, double ti,
            double t1, double t2, double alim, double tpe,
            double dbH, double dbL, double trate) {
        if (deltaTc < 0.0 || deltaTp < 0.0 || droop < 0.0 || ti <= 0.0
                || t1 < 0.0 || t2 < 0.0 || alim < 0.0 || tpe < 0.0
                || dbH < 0.0 || dbL > 0.0 || dbL > dbH || trate < 0.0) {
            log.warn("Invalid WESGOVD parameters at {} {}", busId, genId);
            return null;
        }
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for WESGOVD governor: bus={}, gen={}", busId, genId);
            return null;
        }
        PsseWesgovdGovernor gov = GovernorObjectFactory.createPsseWESGOVDGovernor(
                mach.getId() + "_Gov", "WESGOVD", mach);
        gov.getData().setDeltaTc(deltaTc);
        gov.getData().setDeltaTp(deltaTp);
        gov.getData().setDroop(droop);
        gov.getData().setKp(kp);
        gov.getData().setTi(ti);
        gov.getData().setT1(t1);
        gov.getData().setT2(t2);
        gov.getData().setAlim(alim);
        gov.getData().setTpe(tpe);
        gov.getData().setDbH(dbH);
        gov.getData().setDbL(dbL);
        gov.getData().setTrate(trate);
        return gov;
    }

    /** PSS/E DEGOV1D Woodward diesel governor. */
    public PsseDegov1dGovernor addGovDegov1d(String busId, String genId,
            int droopControl, double t1, double t2, double t3, double k,
            double t4, double t5, double t6, double td, double tmax, double tmin,
            double droop, double te, double dbH, double dbL, double trate) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for DEGOV1D governor: bus={}, gen={}", busId, genId);
            return null;
        }
        PsseDegov1dGovernor gov = GovernorObjectFactory.createPsseDEGOV1DGovernor(
                mach.getId() + "_Gov", "DEGOV1D", mach);
        gov.getData().setDroopControl(droopControl);
        gov.getData().setT1(t1);
        gov.getData().setT2(t2);
        gov.getData().setT3(t3);
        gov.getData().setK(k);
        gov.getData().setT4(t4);
        gov.getData().setT5(t5);
        gov.getData().setT6(t6);
        gov.getData().setTd(td);
        gov.getData().setTmax(tmax);
        gov.getData().setTmin(tmin);
        gov.getData().setDroop(droop);
        gov.getData().setTe(te);
        gov.getData().setDbH(dbH);
        gov.getData().setDbL(dbL);
        gov.getData().setTrate(trate);
        if (!gov.validateParameters()) {
            log.warn("Invalid DEGOV1D parameters at {} {}", busId, genId);
            return null;
        }
        return gov;
    }

    /** PSS/E PIDGOVD hydro turbine-governor. */
    public PssePidgovdGovernor addGovPidgovd(String busId, String genId,
            int feedback, double rperm, double treg, double kp, double ki,
            double kd, double ta, double tb, double dturb, double g0, double g1,
            double p1, double g2, double p2, double p3, double gmax, double gmin,
            double atw, double tw, double velmax, double velmin, double dbH,
            double dbL, double trate) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for PIDGOVD governor: bus={}, gen={}", busId, genId);
            return null;
        }
        PssePidgovdGovernor gov = GovernorObjectFactory.createPssePIDGOVDGovernor(
                mach.getId() + "_Gov", "PIDGOVD", mach);
        gov.getData().setFeedback(feedback);
        gov.getData().setRperm(rperm);
        gov.getData().setTreg(treg);
        gov.getData().setKp(kp);
        gov.getData().setKi(ki);
        gov.getData().setKd(kd);
        gov.getData().setTa(ta);
        gov.getData().setTb(tb);
        gov.getData().setDturb(dturb);
        gov.getData().setG0(g0);
        gov.getData().setG1(g1);
        gov.getData().setP1(p1);
        gov.getData().setG2(g2);
        gov.getData().setP2(p2);
        gov.getData().setP3(p3);
        gov.getData().setGmax(gmax);
        gov.getData().setGmin(gmin);
        gov.getData().setAtw(atw);
        gov.getData().setTw(tw);
        gov.getData().setVelmax(velmax);
        gov.getData().setVelmin(velmin);
        gov.getData().setDbH(dbH);
        gov.getData().setDbL(dbL);
        gov.getData().setTrate(trate);
        if (!gov.validateParameters()) {
            log.warn("Invalid PIDGOVD parameters at {} {}", busId, genId);
            return null;
        }
        return gov;
    }

    /** PSS/E TGOV3D modified IEEE Type-1 steam governor. */
    public PsseTgov3dGovernor addGovTgov3d(String busId, String genId,
            double k, double t1, double t2, double t3, double uo, double uc,
            double pmax, double pmin, double t4, double k1, double t5,
            double k2, double t6, double k3, double ta, double tb, double tc,
            double prmax, double dbH, double dbL, double trate) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for TGOV3D governor: bus={}, gen={}", busId, genId);
            return null;
        }
        PsseTgov3dGovernor gov = GovernorObjectFactory.createPsseTGOV3DGovernor(
                mach.getId() + "_Gov", "TGOV3D", mach);
        gov.getData().setK(k);
        gov.getData().setT1(t1);
        gov.getData().setT2(t2);
        gov.getData().setT3(t3);
        gov.getData().setUo(uo);
        gov.getData().setUc(uc);
        gov.getData().setPmax(pmax);
        gov.getData().setPmin(pmin);
        gov.getData().setT4(t4);
        gov.getData().setK1(k1);
        gov.getData().setT5(t5);
        gov.getData().setK2(k2);
        gov.getData().setT6(t6);
        gov.getData().setK3(k3);
        gov.getData().setTa(ta);
        gov.getData().setTb(tb);
        gov.getData().setTc(tc);
        gov.getData().setPrmax(prmax);
        gov.getData().setDbH(dbH);
        gov.getData().setDbL(dbL);
        gov.getData().setTrate(trate);
        if (!gov.validateParameters()) {
            log.warn("Invalid TGOV3D parameters at {} {}", busId, genId);
            return null;
        }
        return gov;
    }

    /** PSS/E HYGOV2D hydro turbine-governor with speed deadband. */
    public PsseHygov2dGovernor addGovHygov2d(String busId, String genId,
            double kp, double ki, double ka, double t1, double t2, double t3,
            double t4, double t5, double t6, double tr, double rtemp, double r,
            double vgmax, double gmax, double gmin, double pmax, double dbH,
            double dbL, double trate) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for HYGOV2D governor: bus={}, gen={}", busId, genId);
            return null;
        }
        PsseHygov2dGovernor gov = GovernorObjectFactory.createPsseHYGOV2DGovernor(
                mach.getId() + "_Gov", "HYGOV2D", mach);
        gov.getData().setKp(kp);
        gov.getData().setKi(ki);
        gov.getData().setKa(ka);
        gov.getData().setT1(t1);
        gov.getData().setT2(t2);
        gov.getData().setT3(t3);
        gov.getData().setT4(t4);
        gov.getData().setT5(t5);
        gov.getData().setT6(t6);
        gov.getData().setTr(tr);
        gov.getData().setRtemp(rtemp);
        gov.getData().setR(r);
        gov.getData().setVgmax(vgmax);
        gov.getData().setGmax(gmax);
        gov.getData().setGmin(gmin);
        gov.getData().setPmax(pmax);
        gov.getData().setDbH(dbH);
        gov.getData().setDbL(dbL);
        gov.getData().setTrate(trate);
        if (!gov.validateParameters()) {
            log.warn("Invalid HYGOV2D parameters at {} {}", busId, genId);
            return null;
        }
        return gov;
    }

    /** PSS/E WPIDHYD Woodward PID hydro turbine-governor. */
    public PsseWpidhydGovernor addGovWpidhyd(String busId, String genId,
            double treg, double reg, double kp, double ki, double kd,
            double ta, double tb, double velmax, double velmin, double gmax,
            double gmin, double tw, double pmax, double pmin, double damping,
            double g0, double g1, double p1, double g2, double p2, double p3,
            double dbH, double dbL, double trate) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for WPIDHYD governor: bus={}, gen={}", busId, genId);
            return null;
        }
        PsseWpidhydGovernor gov = GovernorObjectFactory.createPsseWPIDHYDGovernor(
                mach.getId() + "_Gov", "WPIDHYD", mach);
        gov.getData().setTreg(treg);
        gov.getData().setReg(reg);
        gov.getData().setKp(kp);
        gov.getData().setKi(ki);
        gov.getData().setKd(kd);
        gov.getData().setTa(ta);
        gov.getData().setTb(tb);
        gov.getData().setVelmax(velmax);
        gov.getData().setVelmin(velmin);
        gov.getData().setGmax(gmax);
        gov.getData().setGmin(gmin);
        gov.getData().setTw(tw);
        gov.getData().setPmax(pmax);
        gov.getData().setPmin(pmin);
        gov.getData().setD(damping);
        gov.getData().setG0(g0);
        gov.getData().setG1(g1);
        gov.getData().setP1(p1);
        gov.getData().setG2(g2);
        gov.getData().setP2(p2);
        gov.getData().setP3(p3);
        gov.getData().setDbH(dbH);
        gov.getData().setDbL(dbL);
        gov.getData().setTrate(trate);
        if (!gov.validateParameters()) {
            log.warn("Invalid WPIDHYD parameters at {} {}", busId, genId);
            return null;
        }
        return gov;
    }

    /** PSS/E GASTWDD Woodward gas-turbine governor. */
    public PsseGastwddGovernor addGovGastwdd(String busId, String genId,
            PsseGastwddGovernorData data) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for GASTWDD governor: bus={}, gen={}", busId, genId);
            return null;
        }
        PsseGastwddGovernor gov = GovernorObjectFactory.createPsseGASTWDDGovernor(
                mach.getId() + "_Gov", "GASTWDD", mach);
        gov.setData(data);
        if (!gov.validateParameters()) {
            log.warn("Invalid GASTWDD parameters at {} {}", busId, genId);
            return null;
        }
        return gov;
    }

    /** PSS/E GAST2AD gas-turbine governor. */
    public PsseGast2adGovernor addGovGast2ad(String busId, String genId,
            PsseGast2adGovernorData data) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for GAST2AD governor: bus={}, gen={}", busId, genId);
            return null;
        }
        PsseGast2adGovernor gov = GovernorObjectFactory.createPsseGAST2ADGovernor(
                mach.getId() + "_Gov", "GAST2AD", mach);
        gov.setData(data);
        if (!gov.validateParameters()) {
            log.warn("Invalid GAST2AD parameters at {} {}", busId, genId);
            return null;
        }
        return gov;
    }

    /**
     * PSS/E IEEEG3 hydro governor, implemented by the matching IEEE 1981
     * Type-3 core controller.
     * Parameters: Tg, Tp, Uo, Uc, Pmax, Pmin, Rperm, Rtemp, Tr, Tw,
     *             A11, A13, A21, A23.
     */
    public Ieee1981Type3HydroGovernor addGovIeeeg3(String busId, String genId,
            double tg, double tp, double uo, double uc,
            double pmax, double pmin, double rperm, double rtemp,
            double tr, double tw, double a11, double a13, double a21, double a23) {
        Machine mach = findMachine(busId, genId);
        if (mach == null) {
            log.warn("Machine not found for IEEEG3 governor: bus={}, gen={}", busId, genId);
            return null;
        }
        Ieee1981Type3HydroGovernor gov = GovernorObjectFactory.createIeee1981Type3HydroGovernor(
                mach.getId() + "_Gov", "IEEEG3", mach);
        gov.getData().setTg(tg);
        gov.getData().setTp(tp);
        gov.getData().setVelOpen(uo);
        gov.getData().setVelClose(uc);
        gov.getData().setPmax(pmax);
        gov.getData().setPmin(pmin);
        gov.getData().setSigma(rperm);
        gov.getData().setDelta(rtemp);
        gov.getData().setTr(tr);
        gov.getData().setTw(tw);
        gov.getData().setA11(a11);
        gov.getData().setA13(a13);
        gov.getData().setA21(a21);
        gov.getData().setA23(a23);
        return gov;
    }

    /**
     * PSS/E IEEEG3D/IEEEG3DU extension of IEEEG3.
     * Parameters append dbH, dbL, and Trate to the shared IEEEG3 record.
     */
    public Ieee1981Type3HydroGovernor addGovIeeeg3d(String busId, String genId,
            double tg, double tp, double uo, double uc,
            double pmax, double pmin, double rperm, double rtemp,
            double tr, double tw, double a11, double a13, double a21, double a23,
            double dbH, double dbL, double trate) {
        if (dbH < 0.0 || dbL > 0.0 || dbL > dbH || trate < 0.0) {
            log.warn("Invalid IEEEG3D deadband/rating at {} {}: dbH={}, dbL={}, Trate={}",
                    busId, genId, dbH, dbL, trate);
            return null;
        }
        Ieee1981Type3HydroGovernor gov = addGovIeeeg3(busId, genId,
                tg, tp, uo, uc, pmax, pmin, rperm, rtemp,
                tr, tw, a11, a13, a21, a23);
        if (gov == null) return null;
        gov.setName("IEEEG3D");
        gov.getData().setDbH(dbH);
        gov.getData().setDbL(dbL);
        gov.getData().setTrate(trate);
        return gov;
    }

    /**
     * PowerWorld/WECC GASTD extension of GAST.
     * Parameters append dbH, dbL, and Trate to the base GAST record.
     */
    public PsseGASTGasTurGovernor addGovGastd(String busId, String genId,
            double r, double t1, double t2, double t3,
            double at, double kt, double vmax, double vmin, double dturb,
            double dbH, double dbL, double trate) {
        if (r <= 0.0 || dbH < 0.0 || dbL > 0.0 || dbL > dbH || trate < 0.0) {
            log.warn("Invalid GASTD parameters at {} {}: R={}, dbH={}, dbL={}, Trate={}",
                    busId, genId, r, dbH, dbL, trate);
            return null;
        }
        PsseGASTGasTurGovernor gov = addGovGast(busId, genId,
                r, t1, t2, t3, at, kt, vmax, vmin, dturb);
        if (gov == null) return null;
        gov.setName("GASTD");
        gov.getData().setDbH(dbH);
        gov.getData().setDbL(dbL);
        gov.getData().setTrate(trate);
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

    /** Attach an LCFB1 secondary Pref controller without replacing the governor. */
    public Lcfb1PrefController addLcfb1(String busId, String genId, Lcfb1Data data) {
        BaseDStabBus<?, ?> bus = network.getDStabBus(busId);
        Machine machine = network.getMachine(busId + "-mach" + genId);
        if (bus == null || machine == null || !machine.hasGovernor()) {
            log.warn("Machine/governor not found for LCFB1: bus={}, gen={}", busId, genId);
            return null;
        }
        return new Lcfb1PrefController(bus, machine, genId, data);
    }

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
        if (converter != null && converter.getActiveElectricalController() != null) {
            Repca1Model controller = new Repca1Model(data, converter);
            converter.getActiveElectricalController().setPlantController(controller);
            return controller;
        }
        Regfma1Model gridForming = findRegfma1(busId, genId);
        if (gridForming != null) {
            Repca1Model controller = new Repca1Model(data, gridForming);
            gridForming.setPlantController(controller);
            return controller;
        }
        log.warn("REGCA1/REEC or REGFMA1 chain not found for REPCA1: bus={}, gen={}",
                busId, genId);
        return null;
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

    public Wtdta1Model addWtdta1(String busId, String genId, Wtdta1Data data) {
        Reeca1Model controller = findReeca1(busId, genId);
        if (controller == null) {
            log.warn("REECA1 not found for WTDTA1: bus={}, gen={}", busId, genId);
            return null;
        }
        Wtdta1Model model = new Wtdta1Model(data);
        windStack(controller).setDriveTrain(model);
        return model;
    }

    /** Attach a PowerWorld WTGT_A record without replacing an existing drive train. */
    public Wtdta1Model addWtgtA(String busId, String genId, WtgtAData data) {
        Reeca1Model controller = findReeca1(busId, genId);
        if (controller == null) {
            log.warn("REECA1 not found for WTGT_A: bus={}, gen={}", busId, genId);
            return null;
        }
        WindControlStack stack = windStack(controller);
        if (stack.getDriveTrain() != null) {
            log.warn("Drive train already exists for WTGT_A: bus={}, gen={}", busId, genId);
            return null;
        }
        BaseDStabBus<?, ?> bus = network.getDStabBus(busId);
        DStabGen gen = (DStabGen) bus.getContributeGen(genId);
        double machineBaseMva = gen.getMvaBase() > 1.0e-9
                ? gen.getMvaBase() : network.getBaseMva();
        Wtdta1Model model = new Wtdta1Model(data, machineBaseMva);
        stack.setDriveTrain(model);
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

    private Regfma1Model findRegfma1(String busId, String genId) {
        BaseDStabBus<?, ?> bus = network.getDStabBus(busId);
        DStabGen gen = bus == null ? null : (DStabGen) bus.getContributeGen(genId);
        return gen != null && gen.getDynamicGenDevice() instanceof Regfma1Model model ? model : null;
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
