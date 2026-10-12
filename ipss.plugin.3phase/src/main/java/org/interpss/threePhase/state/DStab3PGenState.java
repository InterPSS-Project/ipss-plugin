package org.interpss.threePhase.state;

import com.interpss.common.datatype.Complex3x1ValueBean;
import com.interpss.common.datatype.ComplexValueBean;
import com.interpss.dstab.GeneratorType;
import com.interpss.state.dstab.DStabGenState;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.impl.DStab3PGenImpl;

/** Phase generator values plus core sequence and attached machine state. */
public final class DStab3PGenState extends DStabGenState {
    public PhaseFields phase;

    public DStab3PGenState(DStab3PGen source) {
        super(source);
        phase=new PhaseFields(source);
    }
    /** The containing network attaches saved machines after topology construction. */
    public static DStab3PGen create(DStab3PGenState state) {
        var target=new DStab3PGenImpl(); state.map2EObject(target); state.restorePhaseFields(target); return target;
    }
    public void updateEObject(DStab3PGen target) {
        super.updateEObject(target); restorePhaseFields(target);
    }
    public void restorePhaseFields(DStab3PGen target) {phase.restore(target);}

    /** Electrical phase edit replay, independent of attached-model state copying. */
    public static final class PhaseFields {
        public String generatorType;
        public Complex3x3State impedance;
        public Complex3x1ValueBean power;
        public ComplexValueBean scalarPower;
        public PhaseFields(DStab3PGen source) {
            if(source.getClass()!=DStab3PGenImpl.class)
                throw new IllegalArgumentException("Unsupported phase generator implementation: "+source.getClass().getName());
            generatorType=source.getGenType().name();
            impedance=Complex3x3State.capture(source.getZabc(true));
            var vector=source.getPower3Phase(UnitType.PU);
            if(vector!=null)power=new Complex3x1ValueBean(vector);
            scalarPower=new ComplexValueBean(source.getGen());
        }
        public void restore(DStab3PGen target) {
            target.setGenType(GeneratorType.valueOf(generatorType));
            target.setZabc(Complex3x3State.restore(impedance));
            target.setPower3Phase(power==null?null:power.toComplex3x1(),UnitType.PU);
            // Phase assignment calculates a total. Retain the saved independent scalar value.
            target.setGen(scalarPower.toComplex());
        }
    }
}
