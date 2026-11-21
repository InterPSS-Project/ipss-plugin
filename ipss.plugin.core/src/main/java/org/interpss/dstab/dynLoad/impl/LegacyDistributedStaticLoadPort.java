package org.interpss.dstab.dynLoad.impl;

import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.common.DStabSimuException;

/** Static current port with accepted terminal-current history and an owned numerical stamp. */
final class LegacyDistributedStaticLoadPort extends LegacyStaticLoadPort {
    private final Cmldblu1Model owner;
    private Complex acceptedIncoming, precedingStamp;
    private boolean activeSolve;

    LegacyDistributedStaticLoadPort(Cmldblu1Model owner, double assignedPower) {
        super(owner.getLoadBus(), owner.getId() + "_S", owner.getData(), assignedPower,
                owner.getLoadBus().getShuntY(), owner.getStaticFraction());
        this.owner = owner;
    }

    @Override public boolean initStates() {
        if (!super.initStates()) return false;
        acceptedIncoming = owner.distributionBoundaryCurrent();
        precedingStamp = null;
        activeSolve = false;
        return true;
    }

    @Override public void beginNetworkSolution(Context context) throws DStabSimuException {
        super.beginNetworkSolution(context);
        precedingStamp = getEquivY();
        activeSolve = true;
    }

    @Override protected double preparationVoltage() {
        return owner.distributionPreparationVoltage(acceptedIncoming);
    }

    @Override protected void preparedAdmittance(double g, double b) {
        changeNumericalStamp(new Complex(g, b));
    }

    private void changeNumericalStamp(Complex next) {
        Complex delta = next.subtract(getEquivY());
        if (delta.equals(Complex.ZERO)) return;
        var net = (DStabilityNetwork) getDStabBus().getNetwork();
        int row = getDStabBus().getSortNumber();
        net.getYMatrix().addToA(delta, row, row);
        setEquivY(next);
        net.setYMatrixDirty(true);
    }

    @Override public void endNetworkSolution(boolean converged) {
        if (activeSolve) {
            if (converged) acceptedIncoming = owner.distributionBoundaryCurrent();
            else changeNumericalStamp(precedingStamp);
        }
        super.endNetworkSolution(converged);
        activeSolve = false;
        precedingStamp = null;
    }

    @Override public java.util.Map<String, Double> getNamedStates() {
        var states = new java.util.LinkedHashMap<>(super.getNamedStates());
        states.put("STATIC.AcceptedTerminalCurrentReal", acceptedIncoming == null ? 0 : acceptedIncoming.getReal());
        states.put("STATIC.AcceptedTerminalCurrentImag", acceptedIncoming == null ? 0 : acceptedIncoming.getImaginary());
        return java.util.Map.copyOf(states);
    }
}
