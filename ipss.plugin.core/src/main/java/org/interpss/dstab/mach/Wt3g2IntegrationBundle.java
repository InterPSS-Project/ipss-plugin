package org.interpss.dstab.mach;

import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.BaseDStabBus;

/** One numerical owner for the converter and its attached electrical/mechanical controls.
 * Leaf models retain their equations; this class only orders boundary publication and
 * makes projection of the complete trial vector transactional.
 */
public final class Wt3g2IntegrationBundle implements StaggeredEulerParticipant {
    private final Wt3g2Model generator;
    private final DStabGen owner;
    private final BaseDStabBus<?, ?> bus;
    private final Wt3e1Model electrical;
    private final Wt3t1Model mechanical;
    private final Wt3p1Model pitch;
    private final int mechanicalOffset, pitchOffset, count;
    private final double[] saved, rates;

    public Wt3g2IntegrationBundle(Wt3g2Model generator) {
        this.generator = java.util.Objects.requireNonNull(generator);
        owner = generator.getParentGen(); bus = generator.getDStabBus();
        electrical = generator.getElectricalController();
        mechanical = generator.getDriveTrain();
        pitch = mechanical == null ? null : mechanical.getPitchController();
        mechanicalOffset = 5 + (electrical == null ? 0 : electrical.staggeredStateCount());
        pitchOffset = mechanicalOffset + (mechanical == null ? 0 : mechanical.staggeredStateCount());
        count = pitchOffset + (pitch == null ? 0 : pitch.staggeredStateCount());
        saved = new double[count]; rates = new double[count];
        requireAttachments();
    }

    public boolean attachmentsUnchanged() {
        return owner != null && bus != null && owner.getDynamicGenDevice() == generator
                && generator.getParentGen() == owner && generator.getDStabBus() == bus
                && bus.getContributeGen(owner.getId()) == owner
                && generator.getElectricalController() == electrical
                && generator.getDriveTrain() == mechanical
                && (electrical == null || electrical.getGenerator() == generator)
                && (mechanical == null || mechanical.getPitchController() == pitch)
                && (pitch == null || mechanical.getElectricalController() == electrical);
    }

    private void requireAttachments() {
        if (!attachmentsUnchanged()) throw new IllegalStateException("WT3G2 bundle attachment ownership changed");
    }

    @Override public int staggeredStateCount() { return count; }

    @Override public void readStaggeredState(double[] values, int offset) {
        requireAttachments(); buffer(values, offset);
        generator.readStaggeredState(values, offset);
        if (electrical != null) electrical.readStaggeredState(values, offset + 5);
        if (mechanical != null) mechanical.readStaggeredState(values, offset + mechanicalOffset, pitch);
        if (pitch != null) pitch.readStaggeredState(values, offset + pitchOffset);
    }

    private void refreshLinks() {
        if (mechanical != null && electrical != null)
            electrical.setSpeedDeviation(mechanical.getGeneratorSpeed() - 1.0);
        if (pitch != null) mechanical.setPitch(pitch.getPitch());
    }

    @Override public void evaluateStaggered(double step, double[] derivatives, int offset) {
        requireAttachments(); interval(step); buffer(derivatives, offset);
        double ip = generator.activeCommand(), eq = generator.internalVoltageCommand();
        double speed = electrical == null ? 0 : electrical.getSpeedDeviation();
        double blade = mechanical == null ? 0 : mechanical.getPitch();
        double activePower = generator.getP(), reactivePower = generator.getQ(), correction = generator.getDeltaQ();
        try {
            if (!generator.updateAttributes(false))
                throw new IllegalStateException("WT3G2 live power measurement is not finite");
            refreshLinks();
            if (electrical != null) electrical.evaluateStaggered(step, rates, 5);
            if (pitch != null) {
                double deviation = mechanical.getGeneratorSpeed() - 1.0;
                pitch.evaluateStaggered(step, deviation,
                        electrical == null ? deviation : electrical.getSpeedReferenceState(),
                        electrical == null ? generator.getP() : electrical.getPowerOrder(), rates, pitchOffset);
            }
            if (mechanical != null) mechanical.evaluateStaggered(generator.getP(), rates, mechanicalOffset, pitch);
            generator.evaluateConverter(step, rates, 0);
            System.arraycopy(rates, 0, derivatives, offset, count);
        } catch (RuntimeException error) {
            generator.setCommands(ip, eq);
            if (electrical != null) electrical.setSpeedDeviation(speed);
            if (mechanical != null) mechanical.setPitch(blade);
            generator.restoreMeasurements(activePower, reactivePower, correction);
            throw error;
        }
    }

    @Override public void applyStaggeredState(double[] values, int offset, double step) {
        requireAttachments(); interval(step); buffer(values, offset);
        for (int i = 0; i < count; i++) if (!Double.isFinite(values[offset + i]))
            throw new IllegalArgumentException("WT3G2 bundle trial coordinates must be finite");
        readStaggeredState(saved, 0);
        double ip = generator.activeCommand(), eq = generator.internalVoltageCommand();
        double speed = electrical == null ? 0 : electrical.getSpeedDeviation();
        double blade = mechanical == null ? 0 : mechanical.getPitch();
        boolean g = false, e = false, p = false, t = false;
        try {
            generator.applyConverterState(values, offset, step); g = true;
            if (electrical != null) { electrical.applyStaggeredState(values, offset + 5, step); e = true; }
            if (pitch != null) { pitch.applyStaggeredState(values, offset + pitchOffset, step); p = true; }
            if (pitch != null) mechanical.setPitch(pitch.getPitch());
            if (mechanical != null) {
                mechanical.applyStaggeredState(values, offset + mechanicalOffset, step, pitch); t = true;
            }
            refreshLinks();
        } catch (RuntimeException error) {
            try {
                if (p) pitch.applyStaggeredState(saved, pitchOffset, step);
                if (mechanical != null) mechanical.setPitch(blade);
                if (t) mechanical.applyStaggeredState(saved, mechanicalOffset, step, pitch);
                if (e) electrical.applyStaggeredState(saved, 5, step);
                if (g) generator.applyConverterState(saved, 0, step);
                if (electrical != null) electrical.setSpeedDeviation(speed);
                generator.setCommands(ip, eq);
            } catch (RuntimeException restoreFailure) { error.addSuppressed(restoreFailure); }
            throw error;
        }
    }

    private void buffer(double[] values, int offset) { java.util.Objects.checkFromIndexSize(offset, count, values.length); }
    private static void interval(double step) {
        if (!Double.isFinite(step) || step <= 0) throw new IllegalArgumentException("Positive finite integration interval required");
    }
}
