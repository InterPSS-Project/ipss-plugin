package org.interpss.dstab.mach;

/**
 * WECC GENQEJ machine implemented as the GENQEC/core round-rotor equations
 * with the published KIS-dependent saturation input.
 *
 * @see <a href="https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENQEJ.htm">PowerWorld GENQEJ</a>
 * @see <a href="https://www.wecc.org/sites/default/files/documents/meeting/2026/14%20-%20BrooksG%20-%20Machine%20Model%20Specification%20for%20GENQEJ_January%202026.pdf">WECC GENQEJ specification</a>
 */
public final class GenqejMachine extends GenqecMachine {
    private final GenqejData genqejData;

    public GenqejMachine(GenqejData data) {
        super(data.asGenqecData());
        this.genqejData = data;
    }

    public GenqejData getGenqejData() {
        return genqejData;
    }

    @Override
    protected double saturationInput(double airGapFlux, double terminalCurrentMagnitude) {
        return airGapFlux + genqejData.kis() * terminalCurrentMagnitude;
    }
}
