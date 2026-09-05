package org.interpss.fadapter.psse.dyr;

import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.AERODYNAMIC_CONTROLLER;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.CONVERTER_MACHINE;
import static org.interpss.fadapter.psse.dyr.DynamicModelCategory.ELECTRICAL_CONTROLLER;
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
            descriptor("IEEET1", Set.of(), EXCITER, 14, LOADABLE,
                    "org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter",
                    "Exciter%20IEEET1.htm"),
            descriptor("ESST1A", Set.of(), EXCITER, 20, LOADABLE,
                    "org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter",
                    "Exciter%20ESST1A%20and%20ESST1A_GE.htm"),
            descriptor("ESST4B", Set.of("EXST4B"), EXCITER, 17, LOADABLE,
                    "org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter",
                    "Exciter%20ESST4B.htm"),
            descriptor("IEEEG1", Set.of("WSIEG1"), GOVERNOR, 22, LOADABLE,
                    "org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor",
                    "Governor%20IEEEG1%2C%20IEEEG1D%20and%20IEEEG1_GE.htm"),
            descriptor("GGOV1", Set.of(), GOVERNOR, 35, PARTIAL,
                    "org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor",
                    "Governor%20GGOV1%20and%20GGOV1D.htm"),
            descriptor("HYGOV", Set.of(), GOVERNOR, 12, LOADABLE,
                    "org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernor",
                    "Governor%20HYGOV%20and%20HYGOVD.htm"),
            descriptor("PSS2A", Set.of(), STABILIZER, 23, LOADABLE,
                    "org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer",
                    "Stabilizer%20PSS2A.htm"),
            descriptor("REGCA1", Set.of("REGCAU1"), CONVERTER_MACHINE, 15, LOADABLE,
                    "org.interpss.dstab.renewable.Regca1Model", "Machine%20Model%20REGC_A.htm"),
            descriptor("REECA1", Set.of("REECAU1"), ELECTRICAL_CONTROLLER, 51, UNSUPPORTED, "",
                    "Exciter%20REEC_A.htm"),
            descriptor("REPCA1", Set.of("REPCAU1", "REPCTA1", "REPCTAU1"), PLANT_CONTROLLER,
                    34, LOADABLE, "org.interpss.dstab.renewable.Repca1Model",
                    "Plant%20Controller%20REPC_A.htm"),
            descriptor("WTARA1", Set.of("WTARAU1"), AERODYNAMIC_CONTROLLER, 2, UNSUPPORTED, "",
                    "Aerodynamic%20Model%20WTGA_A.htm"),
            descriptor("WTPTA1", Set.of("WTPTAU1"), PITCH_CONTROLLER, 10, UNSUPPORTED, "",
                    "Pitch%20Controller%20WTGPT_A.htm"),
            descriptor("WTTQA1", Set.of("WTTQAU1"), TORQUE_CONTROLLER, 16, UNSUPPORTED, "",
                    "Pref%20Controller%20WTGTRQ_A.htm"),
            descriptor("REGFMA1", Set.of(), CONVERTER_MACHINE, 19, UNSUPPORTED, "",
                    "Machine%20Model%20REGFM_A1.htm")
    );

    private static final Map<String, DynamicModelDescriptor> BY_NAME = buildIndex();

    private DynamicModelCatalog() {
    }

    public static Collection<DynamicModelDescriptor> texas2kModels() {
        return TEXAS2K;
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
        for (DynamicModelDescriptor descriptor : TEXAS2K) {
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
        return new DynamicModelDescriptor(name, aliases, category, parameterCount, status,
                runtimeClass, URI.create(PW + powerWorldPage));
    }
}
