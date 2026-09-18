package org.interpss.dstab.mach;

/**
 * PSS/E GENTPJ1 synchronous machine.
 *
 * <p>The differential equations are shared with GENQEC/GENQEJ. GENTPJ1's
 * distinguishing behavior is quadratic saturation evaluated at air-gap flux
 * plus {@code Kis} times terminal-current magnitude.</p>
 */
public final class Gentpj1Machine extends GenqecMachine {
    private final Gentpj1Data gentpj1Data;

    public Gentpj1Machine(Gentpj1Data data) {
        super(data.asGenqecData());
        this.gentpj1Data = data;
    }

    public Gentpj1Data getGentpj1Data() {
        return gentpj1Data;
    }

    @Override
    protected double saturationInput(double airGapFlux, double terminalCurrentMagnitude) {
        return airGapFlux + gentpj1Data.kis() * terminalCurrentMagnitude;
    }
}
