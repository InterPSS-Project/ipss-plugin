package org.interpss.dstab.control.exc;

/** Physical summation-port contract for an explicitly selected BASOEL2 equation dialect. */
public interface Basoel2ExciterPort extends Oel3cExciterPort {
    /** Live exciter voltage reference, in terminal-voltage per unit. */
    double basoel2VoltageReference();
}
