package org.interpss.dstab.renewable;

import java.util.*;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** REPCC plant composition for the REGCA1/REECA1 command boundary. Absolute
 * machine-base kernel outputs are translated to downstream reference offsets;
 * physical shunts advance only after the enclosing solver accepts an endpoint. */
public final class RepccModel implements ICMLStateProvider {
    private final RepccData data;
    private final Regca1Model converter;
    private final Map<RepccData.Shunt,Complex> nominalShunts;
    private RepccNetworkBoundary boundary;
    private RepccKernel kernel;
    private double initialActive,initialReactive;
    private boolean pending;

    public RepccModel(RepccData data,Regca1Model converter,Map<RepccData.Shunt,Complex> nominalSystemShunts){
        this.data=Objects.requireNonNull(data);this.converter=Objects.requireNonNull(converter);
        nominalShunts=Map.copyOf(nominalSystemShunts);
    }
    public RepccModel(RepccData data,Regca1Model converter){this(data,converter,Map.of());}
    public void initialize(double active,double reactiveReference,boolean reactiveCommand){
        if(converter.getIntegrationScheme()!=Regca1Model.IntegrationScheme.MODIFIED_EULER)
            throw new IllegalArgumentException("REPCC requires the staged modified-Euler converter path");
        var nextBoundary=new RepccNetworkBoundary(data,converter.getParentGen(),converter.getDStabBus());
        var measured=nextBoundary.measure(local()).control();
        var nextKernel=new RepccKernel(data,reactiveCommand);
        nextKernel.initialize(new RepccInitialization.Measurements(measured.regulatedVoltage(),measured.branchCurrent(),
                measured.active(),measured.reactive(),active,reactiveReference));
        nextBoundary.initializeShunts(nominalShunts);
        initialActive=active;initialReactive=reactiveReference;kernel=nextKernel;boundary=nextBoundary;pending=false;
    }
    private RepccNetworkBoundary.Local local(){
        Complex voltage=converter.getDStabBus().getVoltage();double a=voltage.getArgument();
        Complex current=new Complex(converter.getIp(),-converter.getIq()).multiply(new Complex(Math.cos(a),Math.sin(a)));
        return new RepccNetworkBoundary.Local(voltage.multiply(current.conjugate()),current);
    }
    public RepccKernel.Input measurement(){requireInitialized();return boundary.measure(local()).control();}
    public void step(double dt,int stage){
        requireInitialized();
        if(!converter.getParentGen().isActive())return;
        if(stage==0&&pending&&dt>0)throw new IllegalStateException("REPCC corrector is pending");
        kernel.step(dt,measurement(),stage);
        if(dt>0)pending=stage==0;
    }
    public List<RepccShuntController.Action> acceptedEndpoint(){
        requireInitialized();
        if(pending)throw new IllegalStateException("Complete REPCC corrector before physical shunt dispatch");
        if(!converter.getParentGen().isActive())return List.of();
        return boundary.advanceShunts(kernel.time());
    }
    public double activeIncrement(){requireInitialized();return kernel.output(measurement()).active()-initialActive;}
    public double reactiveIncrement(){requireInitialized();return kernel.output(measurement()).reactive()-initialReactive;}
    public RepccKernel getKernel(){requireInitialized();return kernel;}
    public RepccNetworkBoundary getBoundary(){requireInitialized();return boundary;}
    public RepccData getData(){return data;}
    public Regca1Model getConverter(){return converter;}
    @Override public Map<String,Double> getNamedStates(){
        if(kernel==null)return Map.of();var result=new LinkedHashMap<>(kernel.getNamedStates());
        boundary.shuntController().getNamedStates().forEach((key,value)->result.put("switching."+key,value));
        return Collections.unmodifiableMap(result);
    }
    private void requireInitialized(){if(kernel==null)throw new IllegalStateException("Initialize REPCC plant first");}
}
