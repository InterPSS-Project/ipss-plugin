package org.interpss.dstab.control.util;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.mach.Machine;

/** A governor that can initialize its states and reference from a source's
 * settled initial shaft power. Called before integration plans are bound and
 * before physical initial events. Implementations must reset initialization
 * histories without integrating or advancing time, using their shared model
 * equations. This is not a running-network power-setpoint operation. */
public interface InitialMechanicalPowerPreparation {
    boolean initializeMechanicalOperatingPoint(BaseDStabBus<?, ?> bus, Machine machine);
}
