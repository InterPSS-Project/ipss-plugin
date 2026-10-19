package org.interpss.dstab.control.pss.psse.ieeest;

import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;

/** Model-specific T5=0 convention; all ordinary washout storage and integration are inherited. */
public final class IeeestTerminalBlock extends WashoutControlBlock {
    private final boolean zeroNumeratorLag;

    public IeeestTerminalBlock(double gain,double time,double numerator) {
        super(gain,time);
        zeroNumeratorLag=numerator==0.0 && time>0.0;
    }
    public boolean isZeroNumeratorLag(){return zeroNumeratorLag;}
    @Override public boolean initStateU0(double input) {
        if(!zeroNumeratorLag)return super.initStateU0(input);
        setU(input);setStateX(input);return true;
    }
    @Override public boolean initStateY0(double output) {
        if(!zeroNumeratorLag)return super.initStateY0(output);
        setU(output);setStateX(output);return true;
    }
    @Override public double getY(){return zeroNumeratorLag?getStateX():super.getY();}
    @Override protected double dX_dt(double input){return zeroNumeratorLag?(input-getStateX())/getT():super.dX_dt(input);}
}
