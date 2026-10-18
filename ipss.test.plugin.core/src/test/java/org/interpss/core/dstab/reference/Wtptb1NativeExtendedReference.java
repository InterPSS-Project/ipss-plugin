package org.interpss.core.dstab.reference;

import java.util.List;
import org.interpss.dstab.renewable.Wtptb1Data;
import org.interpss.core.dstab.reference.Wtptb1NativeReference.Profile;

/** PSS/E 36.7 WTPTB1 native branch inputs and unmodified selected state/network rows.
 * Checkpoints contain sample index followed by all original CSV columns:
 * time, pitch, speed integral, compensation integral, plant Pref, Pord, wt deviation,
 * torque Wref, V1, V2, V3, P, Q, machine speed deviation. Every nonnegative input
 * endpoint, both event sides and the extra fine terminal tick are retained.
 * Complete raw CSV/prehistory, scripts, parameters and cold provenance stay private.
 */
public final class Wtptb1NativeExtendedReference {
    private Wtptb1NativeExtendedReference() {}
    public static List<Profile> profiles() { return List.of(Wtptb1NativeExtendedPart0.p0(), Wtptb1NativeExtendedPart0.p1(), Wtptb1NativeExtendedPart0.p2(), Wtptb1NativeExtendedPart0.p3(), Wtptb1NativeExtendedPart1.p4(), Wtptb1NativeExtendedPart1.p5(), Wtptb1NativeExtendedPart1.p6(), Wtptb1NativeExtendedPart1.p7(), Wtptb1NativeExtendedPart2.p8(), Wtptb1NativeExtendedPart2.p9()); }
}
