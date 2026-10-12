package org.interpss.threePhase.state;

import com.interpss.common.datatype.ComplexValueBean;
import org.interpss.numeric.datatype.Complex3x3;

/** Value-only matrix storage for phase model states. */
public final class Complex3x3State {
    public ComplexValueBean aa,ab,ac,ba,bb,bc,ca,cb,cc;
    public Complex3x3State(Complex3x3 value) {
        aa=new ComplexValueBean(value.aa); ab=new ComplexValueBean(value.ab); ac=new ComplexValueBean(value.ac);
        ba=new ComplexValueBean(value.ba); bb=new ComplexValueBean(value.bb); bc=new ComplexValueBean(value.bc);
        ca=new ComplexValueBean(value.ca); cb=new ComplexValueBean(value.cb); cc=new ComplexValueBean(value.cc);
    }
    public static Complex3x3State capture(Complex3x3 value){return value==null?null:new Complex3x3State(value);}
    public static Complex3x3 restore(Complex3x3State state){
        if(state==null)return null;
        var value=new Complex3x3();
        value.aa=state.aa.toComplex(); value.ab=state.ab.toComplex(); value.ac=state.ac.toComplex();
        value.ba=state.ba.toComplex(); value.bb=state.bb.toComplex(); value.bc=state.bc.toComplex();
        value.ca=state.ca.toComplex(); value.cb=state.cb.toComplex(); value.cc=state.cc.toComplex();
        return value;
    }
}
