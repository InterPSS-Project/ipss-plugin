package org.interpss.threePhase.dynamic.model;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab3PBus;

import com.interpss.dstab.algo.DynamicSimuMethod;


/**
 *  Currently the modeling is focusing on three-phase balanced and almost balanced condition;
 *  When significant unbalanced condition is considered, negative sequence effect
 *  should be considered. The negative sequence rotor transient is not considered, while
 *  its contribution to the network and to the torque are considered. When interfaced
 *  with the 3 phase network model, the negative sequence is represented by fixed impedance (Ra+X').
 *  The difference between the actual value (depends on the slip) and this fixed value should
 *  be compensated by negative sequence current injection.
 *
 *  Also, the slip needs to be updated according to dSLIP_dt = (Tm-Tep+Ten)/2H
 *
 * @author Qiuhua
 *
 */
public class InductionMotor3PhaseAdapter extends DynLoadModel3Phase {
    /** Adapter values; the underlying motor is an index into the copied bus device pool. */
    public static final class ModelState extends com.interpss.state.net.NameTagState {
        public int motorIndex;
        public boolean busBound,parentBound;
        public double loadPercentage,baseMva;
        public String scriptsValue,extendedId,deviceKind;
        public com.interpss.state.threephase.Complex3x3State zabc,yabc,z120,equivalent;
        public com.interpss.common.datatype.Complex3x1ValueBean current,initialPower;
        public ModelState(InductionMotor3PhaseAdapter source){
            super(source);
            if(source.getClass()!=InductionMotor3PhaseAdapter.class)throw new IllegalArgumentException("Unsupported induction adapter subclass");
            if(!(source.indMotor instanceof org.interpss.dstab.dynLoad.impl.InductionMotorImpl motor)
                    ||motor.getClass()!=org.interpss.dstab.dynLoad.impl.InductionMotorImpl.class)
                throw new IllegalArgumentException("Unsupported adapted induction motor");
            var bus=motor.getDStabBus();
            if(!(bus instanceof DStab3PBus)||source.getDStabBus()!=null&&source.getDStabBus()!=bus
                    ||source.parentBus!=null&&source.parentBus!=bus)
                throw new IllegalArgumentException("Induction adapter and motor must share a phase bus");
            motorIndex=-1;var devices=bus.getDynamicBusDeviceList();
            for(int i=0;i<devices.size();i++)if(devices.get(i)==motor){motorIndex=i;break;}
            if(motorIndex<0)throw new IllegalArgumentException("Adapted motor must belong to the bus device pool");
            busBound=source.getDStabBus()!=null;parentBound=source.parentBus!=null;
            loadPercentage=source.loadPercent;baseMva=source.mvaBase;
            scriptsValue=source.scripts;extendedId=source.extendedDeviceId;deviceKind=source.deviceType==null?null:source.deviceType.name();
            zabc=com.interpss.state.threephase.Complex3x3State.capture(source.Zabc);
            yabc=com.interpss.state.threephase.Complex3x3State.capture(source.Yabc);
            z120=com.interpss.state.threephase.Complex3x3State.capture(source.Z120);
            equivalent=com.interpss.state.threephase.Complex3x3State.capture(source.equivYabc);
            current=source.currInj2Net==null?null:new com.interpss.common.datatype.Complex3x1ValueBean(source.currInj2Net);
            initialPower=source.initLoadPQ3phase==null?null:new com.interpss.common.datatype.Complex3x1ValueBean(source.initLoadPQ3phase);
        }
        public InductionMotor3PhaseAdapter create(DStab3PBus bus,java.util.List<com.interpss.dstab.device.DynamicBusDevice> copies){
            if(motorIndex<0||motorIndex>=copies.size()||!(copies.get(motorIndex) instanceof org.interpss.dstab.dynLoad.impl.InductionMotorImpl motor)
                    ||motor.getDStabBus()!=bus)throw new IllegalArgumentException("Adapter motor reference is missing or foreign");
            var target=new InductionMotor3PhaseAdapter();map2EObject(target);target.indMotor=motor;
            if(busBound)target.setDStabBus(bus);target.parentBus=parentBound?bus:null;
            target.loadPercent=loadPercentage;target.mvaBase=baseMva;
            target.scripts=scriptsValue;target.extendedDeviceId=extendedId;target.deviceType=deviceKind==null?null:com.interpss.dstab.device.DynamicBusDeviceType.valueOf(deviceKind);
            target.Zabc=com.interpss.state.threephase.Complex3x3State.restore(zabc);
            target.Yabc=com.interpss.state.threephase.Complex3x3State.restore(yabc);
            target.Z120=com.interpss.state.threephase.Complex3x3State.restore(z120);
            target.equivYabc=com.interpss.state.threephase.Complex3x3State.restore(equivalent);
            target.currInj2Net=current==null?null:current.toComplex3x1();
            target.initLoadPQ3phase=initialPower==null?null:initialPower.toComplex3x1();
            return target;
        }
    }
    static {
        com.interpss.state.dstab.DynamicBusDeviceState.registerLinkedExtension(InductionMotor3PhaseAdapter.class,
            new com.interpss.state.dstab.DynamicBusDeviceState.LinkedExtension(
                source->new com.google.gson.Gson().toJson(new ModelState((InductionMotor3PhaseAdapter)source)),
                (bus,json,copies)->{
                    if(!(bus instanceof DStab3PBus phase))throw new IllegalArgumentException("Induction adapter requires a phase bus");
                    return new com.google.gson.Gson().fromJson(json,ModelState.class).create(phase,copies);
                }));
    }


	private InductionMotor indMotor = null;

	private Complex3x3 Zabc = null;
	private Complex3x3 Yabc = null;
	private Complex3x3 Z120 = null;

	public InductionMotor3PhaseAdapter(){

	}

    public InductionMotor3PhaseAdapter(InductionMotor motor){
		this.indMotor = motor;
        setStatus(motor.isActive());
		this.parentBus = (DStab3PBus) motor.getDStabBus();

		this.loadPercent = this.indMotor.getLoadPercent();

	}

	@Override
	public void setZabc(Complex3x3 ZAbc) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void setZ120(Complex z1, Complex z2, Complex z0) {
		throw new UnsupportedOperationException();

	}

	@Override
	public Complex3x3 getZabc(boolean machineMVABase) {

		//converted from Yabc
		return getYabc(machineMVABase).inv();

	}

	@Override
	public Complex3x3 getYabc(boolean machineMVABase) {

		double sysMVABase = this.indMotor.getDStabBus().getNetwork().getBaseMva();
		double zMultiFactor=sysMVABase/this.indMotor.getMvaBase(); // convert z from motor base to system base

		if(Yabc == null){
	        // y1 = y2 = equivYSysBase, y0 = 0.0;

			Complex y1 = this.indMotor.getPosSeqEquivY();

			// Yabc on machine base
			Yabc = new Complex3x3(y1,y1,new Complex(0)).ToAbc().multiply(zMultiFactor);
		}

		if(machineMVABase) {
			return Yabc;
		} else {
			return Yabc.multiply(1.0/zMultiFactor);
		}
	}

	@Override
	public Complex3x1 getPower3Phase(UnitType unit) {
		//Power = VABC*conj(equivYABC*VABC - IgenABC)

		Complex3x1 Vabc = ((DStab3PBus)this.indMotor.getDStabBus()).get3PhaseVotlages();
		Complex3x1 Iabc = getIinj2Network3Phase();
		Complex3x1 Pabc = Vabc.multiply(Iabc.conjugate());

		return Pabc;

	}

	@Override
	public Complex3x1 getISource3Phase() {
		Complex ipos = (Complex) this.indMotor.getOutputObject();

		//TODO negative sequence compensation under unbalanced condition
		return Complex3x1.z12_to_abc(new Complex3x1(new Complex(0,0),ipos, new Complex(0,0)));

	}

	@Override
	public Complex3x1 getIinj2Network3Phase() {
		Complex3x1 iInj =getISource3Phase();
		Complex3x1 Vabc = ((DStab3PBus)this.indMotor.getDStabBus()).get3PhaseVotlages();

		iInj = iInj.subtract(getYabc(false).multiply(Vabc));
		return iInj;
	}

	@Override
	public boolean initStates() {
		//TODO here assuming balanced
		Complex initMotorLoad = this.getParentBus().get3PhaseTotalLoad().a_0.multiply(this.loadPercent/100.0);
		this.getInductionMotor().setInitLoadPQ(initMotorLoad);

		boolean flag = this.getInductionMotor().initStates();

		// processing the initLoadPQ3Phase
		Complex initLoad = this.getInductionMotor().getInitLoadPQ();

		//TODO
		//assume 3 phase balanced
		this.setInitLoadPQ3Phase(new Complex3x1(initLoad,initLoad,initLoad));

		return flag;
	}


	@Override
	public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
		return this.getInductionMotor().nextStep( dt, method, flag);
	}

	@Override
	public boolean afterStep(double dt) {
		return this.getInductionMotor().afterStep(dt);
	}

	@Override
	public Object getOutputObject() {

		return getISource3Phase() ;
	}


	public void setInductionMotor(InductionMotor indMotor) {
		this.indMotor = indMotor;

	}


	public InductionMotor getInductionMotor() {

		return this.indMotor;
	}

	@Override
	public void setLoadPercent(double LdPercent){
		this.loadPercent = LdPercent;
		this.indMotor.setLoadPercent(LdPercent);
	}

	  @Override
	public Hashtable<String, Object> getStates(Object ref) {

		  return this.indMotor.getStates(ref);
	}


	@Override
	public boolean updateAttributes(boolean netChange) {
		return this.getInductionMotor().updateAttributes(netChange);
	}

	@Override public String getExtendedDeviceId(){
		return this.indMotor.getExtendedDeviceId();
	}


	@Override
	public double getMVABase(){
		return this.indMotor.getMvaBase();

	}

}
