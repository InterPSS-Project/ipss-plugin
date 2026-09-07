package org.interpss.fadapter.psse.dyr;

import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.AERODYNAMIC_CONTROLLER;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.CONVERTER_MACHINE;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.ELECTRICAL_CONTROLLER;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.DRIVE_TRAIN;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.EXCITER;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.GOVERNOR;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.PITCH_CONTROLLER;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.PLANT_CONTROLLER;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.STABILIZER;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.SYNCHRONOUS_MACHINE;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.TORQUE_CONTROLLER;
import static org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus.LOADABLE;
import static org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus.PARTIAL;
import static org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus.UNSUPPORTED;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Source of truth for PSS/E dynamic-model names and implementation maturity.
 *
 * <p>The first catalog milestone intentionally covers the 17 model names found
 * in the Texas2k Series 24 cases. Approval status is deliberately not inferred
 * from implementation status.</p>
 */
public final class DynamicModelCatalog {
    private static final String PW =
            "https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/";

    private static final List<DynamicModelDescriptor> TEXAS2K = List.of(
            descriptor("GENROU", Set.of("GENROE"), SYNCHRONOUS_MACHINE, 14, LOADABLE,
                    "com.interpss.dstab.mach.RoundRotorMachine", "Machine%20Model%20GENROU.htm"),
            descriptor("GENSAL", Set.of("GENSAE"), SYNCHRONOUS_MACHINE, 12, LOADABLE,
                    "com.interpss.dstab.mach.SalientPoleMachine", "Machine%20Model%20GENSAL.htm"),
            descriptor("EXST1", Set.of(), EXCITER, 12, LOADABLE,
                    "org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter",
                    "Exciter%20EXST1_PTI.htm"),
            descriptorWithVariants("IEEET1", Set.of(), EXCITER, 14, new int[]{15}, LOADABLE,
                    "org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter",
                    "Exciter%20IEEET1.htm"),
            descriptor("ESST1A", Set.of(), EXCITER, 20, LOADABLE,
                    "org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter",
                    "Exciter%20ESST1A%20and%20ESST1A_GE.htm"),
            descriptor("ESST4B", Set.of(), EXCITER, 17, LOADABLE,
                    "org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter",
                    "Exciter%20ESST4B.htm"),
            descriptor("IEEEG1", Set.of("WSIEG1"), GOVERNOR, 22, LOADABLE,
                    "org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor",
                    "Governor%20IEEEG1%2C%20IEEEG1D%20and%20IEEEG1_GE.htm"),
            descriptor("IEEEG1D", Set.of("IEEEG1SDU"), GOVERNOR, 23, LOADABLE,
                    "org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor",
                    "Governor%20IEEEG1%2C%20IEEEG1D%20and%20IEEEG1_GE.htm"),
            descriptor("IEEEG3", Set.of(), GOVERNOR, 14, LOADABLE,
                    "org.interpss.dstab.control.gov.ieee.hydro1981Type3.Ieee1981Type3HydroGovernor",
                    "Governor%20IEEEG3_PTI%20and%20IEEEG3D.htm"),
            descriptor("IEESGO", Set.of(), GOVERNOR, 11, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor",
                    "Governor%20IEESGO%20and%20IEESGOD.htm"),
            descriptor("IEESGOD", Set.of("IEESGODU"), GOVERNOR, 14, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor",
                    "Governor%20IEESGO%20and%20IEESGOD.htm"),
            descriptor("GGOV1", Set.of(), GOVERNOR, 35, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor",
                    "Governor%20GGOV1%20and%20GGOV1D.htm"),
            descriptor("GGOV1D", Set.of("GGOV1DU"), GOVERNOR, 37, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor",
                    "Governor%20GGOV1%20and%20GGOV1D.htm"),
            descriptor("HYGOV", Set.of(), GOVERNOR, 12, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernor",
                    "Governor%20HYGOV%20and%20HYGOVD.htm"),
            descriptor("HYGOVD", Set.of("HYGOVDU"), GOVERNOR, 15, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernor",
                    "Governor%20HYGOV%20and%20HYGOVD.htm"),
            descriptor("PSS2A", Set.of(), STABILIZER, 23, LOADABLE,
                    "org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer",
                    "Stabilizer%20PSS2A.htm"),
            descriptor("REGCA1", Set.of("REGCAU1"), CONVERTER_MACHINE, 15, LOADABLE,
                    "org.interpss.dstab.renewable.Regca1Model", "Machine%20Model%20REGC_A.htm"),
            descriptor("REECA1", Set.of("REECAU1"), ELECTRICAL_CONTROLLER, 51, LOADABLE,
                    "org.interpss.dstab.renewable.Reeca1Model",
                    "Exciter%20REEC_A.htm"),
            descriptorWithVariants("REPCA1", Set.of("REPCAU1", "REPCTA1", "REPCTAU1"),
                    PLANT_CONTROLLER, 34, new int[]{35}, LOADABLE,
                    "org.interpss.dstab.renewable.Repca1Model",
                    "Plant%20Controller%20REPC_A.htm"),
            descriptor("WTARA1", Set.of("WTARAU1"), AERODYNAMIC_CONTROLLER, 2, LOADABLE,
                    "org.interpss.dstab.renewable.Wtara1Model",
                    "Aerodynamic%20Model%20WTGA_A.htm"),
            descriptor("WTPTA1", Set.of("WTPTAU1"), PITCH_CONTROLLER, 10, LOADABLE,
                    "org.interpss.dstab.renewable.Wtpta1Model",
                    "Pitch%20Controller%20WTGPT_A.htm"),
            descriptor("WTTQA1", Set.of("WTTQAU1"), TORQUE_CONTROLLER, 16, LOADABLE,
                    "org.interpss.dstab.renewable.Wttqa1Model",
                    "Pref%20Controller%20WTGTRQ_A.htm"),
            descriptor("REGFMA1", Set.of(), CONVERTER_MACHINE, 19, LOADABLE,
                    "org.interpss.dstab.renewable.Regfma1Model",
                    "Machine%20Model%20REGFM_A1.htm")
    );

    private static final List<DynamicModelDescriptor> ADDITIONAL = List.of(
            descriptor("WTDTA1", Set.of("WTDTAU1", "WTDAT1"), DRIVE_TRAIN, 5, LOADABLE,
                    "org.interpss.dstab.renewable.Wtdta1Model",
                    "Governor%20WTDTA1.htm"),
            descriptorWithVariants("PSS2B", Set.of(), STABILIZER, 27,
                    new int[]{31}, LOADABLE,
                    "org.interpss.dstab.control.pss.ieee.y1992.pss2b.Ieee1992PSS2BStabilizer",
                    "Stabilizer%20PSS2B.htm"),
            descriptor("PSS2C", Set.of(), STABILIZER, 35, LOADABLE,
                    "org.interpss.dstab.control.pss.ieee.y2016.pss2c.Ieee2016PSS2CStabilizer",
                    "Stabilizer%20PSS2C.htm"),
            descriptor("PSS3B", Set.of(), STABILIZER, 19, LOADABLE,
                    "org.interpss.dstab.control.pss.ieee.y2005.pss3b.Ieee2005PSS3BStabilizer",
                    "Stabilizer%20PSS3B.htm"),
            descriptor("PSS4B", Set.of(), STABILIZER, 75, LOADABLE,
                    "org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizer",
                    "Stabilizer%20PSS4B.htm"),
            descriptor("PSS3C", Set.of(), STABILIZER, 24, LOADABLE,
                    "org.interpss.dstab.control.pss.ieee.y2016.pss3c.Ieee2016PSS3CStabilizer",
                    "Stabilizer%20PSS3C.htm"),
            descriptor("PSS4C", Set.of(), STABILIZER, 94, LOADABLE,
                    "org.interpss.dstab.control.pss.ieee.y2016.pss4c.Ieee2016PSS4CStabilizer",
                    "Stabilizer%20PSS4C.htm"),
            descriptor("PSS5C", Set.of(), STABILIZER, 21, LOADABLE,
                    "org.interpss.dstab.control.pss.ieee.y2016.pss5c.Ieee2016PSS5CStabilizer",
                    "Stabilizer%20PSS5C.htm"),
            descriptorWithVariants("PSS6C", Set.of(), STABILIZER, 34,
                    new int[]{35}, LOADABLE,
                    "org.interpss.dstab.control.pss.ieee.y2016.pss6c.Ieee2016PSS6CStabilizer",
                    "Stabilizer%20PSS6C.htm"),
            descriptorWithVariants("PSS7C", Set.of(), STABILIZER, 38,
                    new int[]{39}, LOADABLE,
                    "org.interpss.dstab.control.pss.ieee.y2016.pss7c.Ieee2016PSS7CStabilizer",
                    "Stabilizer%20PSS7C.htm"),
            descriptor("GENQEC", Set.of(), SYNCHRONOUS_MACHINE, 20, LOADABLE,
                    "org.interpss.dstab.mach.GenqecMachine", "Machine%20Model%20GENQEC.htm"),
            descriptor("GENQEJ", Set.of("GENQEJU"), SYNCHRONOUS_MACHINE, 20, LOADABLE,
                    "org.interpss.dstab.mach.GenqejMachine", "Machine%20Model%20GENQEJ.htm"),
            descriptor("GENCLS", Set.of(), SYNCHRONOUS_MACHINE, 2, LOADABLE,
                    "com.interpss.dstab.mach.EConstMachine", "Machine%20Model%20GENCLS.htm"),
            descriptor("ST2CUT", Set.of("WSCCST"), STABILIZER, 20, LOADABLE,
                    "org.interpss.dstab.control.pss.psse.st2cut.St2cutStabilizer",
                    "Stabilizer%20ST2CUT.htm"),
            descriptor("IEEEST", Set.of(), STABILIZER, 19, LOADABLE,
                    "org.interpss.dstab.control.pss.psse.ieeest.IeeestStabilizer",
                    "Stabilizer%20IEEEST.htm"),
            descriptor("PSS1A", Set.of(), STABILIZER, 14, LOADABLE,
                    "org.interpss.dstab.control.pss.ieee.y1992.pss1a.Ieee1992PSS1AStabilizer",
                    "Stabilizer%20PSS1A.htm"),
            descriptor("HYG3", Set.of("HYG3U1"), GOVERNOR, 37, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.hyg3.PsseHyg3Governor",
                    "Governor%20HYG3.htm"),
            descriptorWithVariants("H6E", Set.of("H6EU1"), GOVERNOR, 63,
                    new int[]{61, 69}, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.h6e.PsseH6eGovernor",
                    "Governor%20H6E.htm"),
            descriptor("HYGOVR", Set.of("HYGOVR1", "HYGOVRU"), GOVERNOR, 26, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.hygovr.PsseHygovrGovernor",
                    "Governor%20HYGOVR.htm"),
            descriptor("LCFB1", Set.of("LCFB1_PTI"), GOVERNOR, 9, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.lcfb1.Lcfb1PrefController",
                    "Pref%20Controller%20LCFB1.htm"),
            descriptor("TGOV1", Set.of(), GOVERNOR, 7, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor",
                    "Governor%20TGOV1%20and%20TGOV1D.htm"),
            descriptor("TGOV1D", Set.of("TGOV1DU"), GOVERNOR, 10, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor",
                    "Governor%20TGOV1%20and%20TGOV1D.htm"),
            descriptor("IEEEG3D", Set.of("IEEEG3DU"), GOVERNOR, 17, LOADABLE,
                    "org.interpss.dstab.control.gov.ieee.hydro1981Type3.Ieee1981Type3HydroGovernor",
                    "Governor%20IEEEG3_PTI%20and%20IEEEG3D.htm"),
            descriptor("WESGOVD", Set.of("WESGOVDU"), GOVERNOR, 12, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.wesgov.PsseWesgovdGovernor",
                    "Governor%20WESGOV%20and%20WESGOVD.htm"),
            descriptor("DEGOV1D", Set.of("DEGOV1DU"), GOVERNOR, 16, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.degov1.PsseDegov1dGovernor",
                    "Governor%20DEGOV1%20and%20DEGOV1D.htm"),
            descriptor("PIDGOVD", Set.of("PIDGOVDU"), GOVERNOR, 24, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.pidgov.PssePidgovdGovernor",
                    "Governor%20PIDGOV%20and%20PIDGOVD.htm"),
            descriptor("TGOV3D", Set.of("TGOV3DU"), GOVERNOR, 21, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.tgov3.PsseTgov3dGovernor",
                    "Governor%20TGOV3%20and%20TGOV3D.htm"),
            descriptor("HYGOV2D", Set.of("HYGOV2DU"), GOVERNOR, 19, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.hygov2.PsseHygov2dGovernor",
                    "Governor%20HYGOV2%20and%20HYGOV2D.htm"),
            descriptor("WPIDHYD", Set.of("WPIDHYDU"), GOVERNOR, 24, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.wpidhy.PsseWpidhydGovernor",
                    "Governor%20WPIDHY%20and%20WPIDHYD.htm"),
            descriptor("GASTWDD", Set.of("GASTWDDU"), GOVERNOR, 34, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.gastwd.PsseGastwddGovernor",
                    "Governor%20GASTWD%20and%20GASTWDD.htm"),
            descriptor("GAST2AD", Set.of("GAST2ADU"), GOVERNOR, 33, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.gast2a.PsseGast2adGovernor",
                    "Governor%20GAST2A.htm"),
            descriptor("GAST", Set.of(), GOVERNOR, 9, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.gast.PsseGASTGasTurGovernor",
                    "Governor%20GAST_PTI%20and%20GASTD.htm"),
            descriptor("GASTD", Set.of("GASTDU"), GOVERNOR, 12, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.gast.PsseGASTGasTurGovernor",
                    "Governor%20GAST_PTI%20and%20GASTD.htm"),
            descriptor("EXAC1", Set.of(), EXCITER, 17, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter",
                    "Exciter%20EXAC1.htm"),
            descriptor("ESURRY", Set.of("EXAC1M"), EXCITER, 20, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.esurry.EsurryExciter",
                    "Exciter.htm"),
            descriptor("EXAC1A", Set.of(), EXCITER, 17, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.exac1a.Exac1aExciter",
                    "Exciter%20EXAC1A.htm"),
            descriptor("EXAC2", Set.of(), EXCITER, 23, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.exac2.Exac2Exciter",
                    "Exciter%20EXAC2.htm"),
            descriptor("ESAC1A", Set.of(), EXCITER, 19, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.esac1a.Esac1aExciter",
                    "Exciter%20ESAC1A.htm"),
            descriptor("AC1C", Set.of(), EXCITER, 23, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.ac1c.Ac1cExciter",
                    "Exciter%20AC1C.htm"),
            descriptor("AC2C", Set.of(), EXCITER, 25, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.ac2c.Ac2cExciter",
                    "Exciter%20AC2C.htm"),
            descriptor("AC3C", Set.of(), EXCITER, 30, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.ac3c.Ac3cExciter",
                    "Exciter%20AC3C.htm"),
            descriptor("AC4C", Set.of(), EXCITER, 12, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.ac4c.Ac4cExciter",
                    "Exciter%20AC4C.htm"),
            descriptor("AC5C", Set.of(), EXCITER, 21, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.ac5c.Ac5cExciter",
                    "Exciter%20AC5C.htm"),
            descriptor("AC6C", Set.of(), EXCITER, 27, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.ac6c.Ac6cExciter",
                    "Exciter%20AC6C.htm"),
            descriptor("ESAC2A", Set.of(), EXCITER, 22, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.esac2a.Esac2aExciter",
                    "Exciter%20ESAC2A.htm"),
            descriptor("ESAC5A", Set.of(), EXCITER, 15, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.esac5a.Esac5aExciter",
                    "Exciter%20ESAC5A.htm"),
            descriptorWithVariants("ESAC6A", Set.of(), EXCITER, 23,
                    new int[]{24}, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.esac6a.Esac6aExciter",
                    "Exciter%20ESAC6A.htm"),
            descriptor("ESDC1A", Set.of(), EXCITER, 16, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.esdc1a.Esdc1aExciter",
                    "Exciter%20ESDC1A.htm"),
            descriptor("ESDC2A", Set.of(), EXCITER, 16, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aExciter",
                    "Exciter%20ESDC2A.htm"),
            descriptor("IEEEX1", Set.of(), EXCITER, 16, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.ieeex1.Ieeex1Exciter",
                    "Exciter%20IEEEX1.htm"),
            descriptor("EXDC2", Set.of(), EXCITER, 16, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.exdc2.Exdc2Exciter",
                    "Exciter%20EXDC2_PTI.htm"),
            descriptor("EXDC2A", Set.of(), EXCITER, 16, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.exdc2a.Exdc2aExciter",
                    "Exciter%20EXDC2A.htm"),
            descriptor("AC8B", Set.of(), EXCITER, 21, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.ac8b.Ac8bExciter",
                    "Exciter%20AC8B.htm"),
            descriptor("ESAC4A", Set.of(), EXCITER, 10, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.esac4a.Esac4aExciter",
                    "Exciter%20ESAC4A.htm"),
            descriptor("EXAC4", Set.of(), EXCITER, 10, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.exac4.Exac4Exciter",
                    "Exciter%20EXAC4.htm"),
            descriptorWithVariants("DC4B", Set.of("ESDC4B"), EXCITER, 20,
                    new int[]{21}, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.dc4b.Dc4bExciter",
                    "Exciter%20DC4B%20and%20ESDC4B.htm"),
            descriptorWithVariants("DC3A", Set.of("ESDC3A"), EXCITER, 12,
                    new int[]{13}, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.dc3a.Dc3aExciter",
                    "Exciter%20DC3A%20and%20ESDC3A.htm"),
            descriptor("ST6B", Set.of("ESST6B"), EXCITER, 17, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.st6b.St6bExciter",
                    "Exciter%20ESST6B%20and%20ST6B.htm"),
            descriptorWithVariants("ST7B", Set.of("ESST7B"), EXCITER, 16,
                    new int[]{17}, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.st7b.St7bExciter",
                    "Exciter%20ESST7B%20and%20ST7B.htm"),
            descriptor("ESST2A", Set.of(), EXCITER, 13, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.esst2a.Esst2aExciter",
                    "Exciter%20ESST2A.htm"),
            descriptor("EXST2", Set.of(), EXCITER, 13, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.exst2.Exst2Exciter",
                    "Exciter%20EXST2.htm"),
            descriptor("ST5B", Set.of("ESST5B"), EXCITER, 18, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.st5b.St5bExciter",
                    "Exciter%20ESST5B%20and%20ST5B.htm"),
            descriptor("REXSYS", Set.of(), EXCITER, 31, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.rexsys.RexsysExciter",
                    "Exciter%20REXSY1.htm"),
            descriptorWithVariants("AC7B", Set.of("ESAC7B"), EXCITER, 27,
                    new int[]{28}, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.ac7b.Ac7bExciter",
                    "Exciter%20AC7B%20and%20ESAC7B.htm"),
            descriptor("IEEET4", Set.of("EXDC4"), EXCITER, 11, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.ieeet4.Ieeet4Exciter",
                    "Exciter%20IEEET4.htm"),
            descriptor("ESST3A", Set.of(), EXCITER, 21, LOADABLE,
                    "org.interpss.dstab.control.exc.ieee.y2005.st3a.IEEE2005ST3AExciter",
                    "Exciter%20ESST3A.htm"),
            descriptor("SCRX", Set.of(), EXCITER, 8, LOADABLE,
                    "org.interpss.dstab.control.exc.psse.scrx.ScrxExciter",
                    "Exciter%20SCRX.htm"),
            descriptor("REECB1", Set.of(), ELECTRICAL_CONTROLLER, 30, LOADABLE,
                    "org.interpss.dstab.renewable.Reecb1Model",
                    "Exciter%20REEC_B.htm")
    );

    private static final List<DynamicModelDescriptor> ALL = java.util.stream.Stream
            .concat(TEXAS2K.stream(), ADDITIONAL.stream()).toList();

    private static final Map<String, DynamicModelDescriptor> BY_NAME = buildIndex();

    private DynamicModelCatalog() {
    }

    public static Collection<DynamicModelDescriptor> texas2kModels() {
        return TEXAS2K;
    }

    public static Collection<DynamicModelDescriptor> allModels() {
        return ALL;
    }

    public static Optional<DynamicModelDescriptor> find(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return Optional.ofNullable(BY_NAME.get(DynamicModelDescriptor.normalizeName(name)));
    }

    public static String canonicalName(String name) {
        return find(name).map(DynamicModelDescriptor::canonicalName)
                .orElseGet(() -> DynamicModelDescriptor.normalizeName(name));
    }

    private static Map<String, DynamicModelDescriptor> buildIndex() {
        Map<String, DynamicModelDescriptor> index = new LinkedHashMap<>();
        for (DynamicModelDescriptor descriptor : ALL) {
            for (String name : descriptor.allNames()) {
                DynamicModelDescriptor previous = index.putIfAbsent(name, descriptor);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate dynamic-model name or alias: " + name);
                }
            }
        }
        return Map.copyOf(index);
    }

    private static DynamicModelDescriptor descriptor(String name, Set<String> aliases,
            DynamicModelCategory category, int parameterCount, DynamicModelSupportStatus status,
            String runtimeClass, String powerWorldPage) {
        return new DynamicModelDescriptor(name, aliases, category,
                DynamicModelRecordSchema.exact(parameterCount), status,
                runtimeClass, URI.create(PW + powerWorldPage));
    }

    private static DynamicModelDescriptor descriptorWithVariants(String name, Set<String> aliases,
            DynamicModelCategory category, int primaryParameterCount, int[] alternativeCounts,
            DynamicModelSupportStatus status, String runtimeClass, String powerWorldPage) {
        return new DynamicModelDescriptor(name, aliases, category,
                DynamicModelRecordSchema.variants(primaryParameterCount, alternativeCounts), status,
                runtimeClass, URI.create(PW + powerWorldPage));
    }
}
