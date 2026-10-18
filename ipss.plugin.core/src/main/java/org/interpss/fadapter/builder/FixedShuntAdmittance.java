package org.interpss.fadapter.builder;

import java.util.Optional;
import org.apache.commons.math3.complex.Complex;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import com.interpss.core.aclf.ShuntCompensator;

/** Original single-device system-base admittance, including out-of-service G.
 * Object-owned transient metadata avoids global registries and does not replace
 * another extension. It is not an EMF serialized feature: deserialized/copied
 * devices must be reimported or supplied explicit nominal data before switching.
 */
public final class FixedShuntAdmittance extends AdapterImpl {
    private final Complex nominal;
    private FixedShuntAdmittance(Complex nominal) { this.nominal=nominal; }

    static void retain(ShuntCompensator shunt,double g,double b) {
        if(!Double.isFinite(g)||!Double.isFinite(b))
            throw new IllegalArgumentException("Finite fixed-shunt admittance required");
        shunt.eAdapters().add(new FixedShuntAdmittance(new Complex(g,b)));
    }

    /** No aggregate-bus inference. Modified B or a multi-step device invalidates
     * the retained input; unknown or ambiguous metadata fails closed. */
    public static Optional<Complex> find(ShuntCompensator shunt) {
        if(shunt==null||shunt.getSteps()!=1)return Optional.empty();
        Complex found=null;
        for(var adapter:shunt.eAdapters())if(adapter instanceof FixedShuntAdmittance input) {
            if(found!=null||input.nominal.getImaginary()!=shunt.getB())return Optional.empty();
            found=input.nominal;
        }
        return Optional.ofNullable(found);
    }
}
