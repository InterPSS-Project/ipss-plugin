package org.interpss.dstab.mach;

import com.interpss.dstab.mach.Machine;

/** PSS/E PAGV2 36.7, section 15.4.4, equation 15.2 (Flux0 torque convention). */
final class PsseSwingEquation {
    private PsseSwingEquation() {}

    static double speedDerivative(Machine machine, double speed, double baseFrequency) {
        double damping = machine.getD() * .01 * baseFrequency;
        // Mechanical power and speed-sensitive load power are converted to
        // torque at actual speed. Pe is electromagnetic torque in the Flux0
        // stator realization, whose internal voltage omits rotor-speed scaling.
        return ((machine.getPm() - damping * (speed - 1)) / speed
                - machine.getPe() - machine.getPe_NegSeq()) / (2 * machine.getH());
    }
}
