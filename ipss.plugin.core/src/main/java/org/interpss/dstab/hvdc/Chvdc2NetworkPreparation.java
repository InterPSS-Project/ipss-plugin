package org.interpss.dstab.hvdc;

import java.util.Objects;
import com.interpss.core.aclf.hvdc.*;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import org.interpss.numeric.datatype.Unit.UnitType;

/** Prepares an exclusively owned CHVDC2 runtime from a named solved static link.
 * Does not attach devices, disable static injections, or change the network.
 * The initial current reference is the solved line current, not a live static
 * power schedule. Auxiliary routing and network lifecycle ownership are separate.
 */
public final class Chvdc2NetworkPreparation {
    private Chvdc2NetworkPreparation() {}

    public record OperatingPoint(double rectifierVolts,double inverterVolts,double currentAmperes,
            double initialTimeSeconds) {}
    /** Coefficients are prepared once using outer H, independent of subsequent local steps.
     * The mutable runtime belongs exclusively to the caller of this preparation. */
    public record Binding(HvdcLine2TLCC<?> owner,OperatingPoint operatingPoint,
            double networkIntervalSeconds,double frequencyHz,double effectiveHenries,double capacitanceFarads,
            Chvdc2CoupledRuntime runtime) {}

    public static Binding prepare(BaseDStabNetwork<?,?> network,Chvdc2Parameters parameters,
            double networkIntervalSeconds,double initialTimeSeconds) {
        Objects.requireNonNull(network);Objects.requireNonNull(parameters);
        if(!Double.isFinite(networkIntervalSeconds)||networkIntervalSeconds<=0||!Double.isFinite(initialTimeSeconds))
            throw new IllegalArgumentException("Positive finite outer interval and finite initial time required");
        if(!network.isLfConverged())throw new IllegalStateException("Solve load flow before preparing CHVDC2");
        HvdcLine2TLCC<?> owner=null;
        for(var branch:network.getSpecialBranchList()) {
            if(branch instanceof HvdcLine2TLCC<?> candidate&&parameters.linkName().equals(candidate.getName())) {
                if(owner!=null)throw new IllegalArgumentException("Ambiguous static DC-link name: "+parameters.linkName());
                owner=candidate;
            }
        }
        if(owner==null)throw new IllegalArgumentException("No static DC-link owner named "+parameters.linkName());
        if(!owner.isActive()||!owner.isInitialized()||owner.getNetwork()!=network)
            throw new IllegalArgumentException("Active solved DC-link owner required");
        if(owner.getOperationMode()!=HvdcOperationMode.REC1_INV1||owner.getRectifier2()!=null
                ||owner.getInverter2()!=null||owner.isPuBasedPowerFlowAlgo())
            throw new IllegalArgumentException("CHVDC2 requires a single converter pair solved in physical units");
        if(owner.getDcLineControlMode()!=HvdcControlMode.DC_POWER&&owner.getDcLineControlMode()!=HvdcControlMode.DC_CURRENT)
            throw new IllegalArgumentException("Solved power or current controlled link required");
        if(owner.getExtensionObject() instanceof LccSourceControls source
                &&(source.rectifierCapacitorOhms()!=0||source.inverterCapacitorOhms()!=0))
            throw new IllegalArgumentException("Capacitor-commutated converters are not qualified");
        var rectifier=owner.getRectifier();var inverter=owner.getInverter();
        if(rectifier==null||inverter==null||rectifier.getConverterType()!=ConverterType.RECTIFIER
                ||inverter.getConverterType()!=ConverterType.INVERTER)
            throw new IllegalArgumentException("Oriented rectifier/inverter equipment required");
        if(!(rectifier.getBus() instanceof BaseDStabBus<?,?> rb)||!(inverter.getBus() instanceof BaseDStabBus<?,?> ib)
                ||rb==ib||!rb.isActive()||!ib.isActive()||rb.getNetwork()!=network||ib.getNetwork()!=network
                ||owner.getFromBus()!=rb||owner.getToBus()!=ib)
            throw new IllegalArgumentException("Distinct active terminals in the owning dynamic network required");
        if(rectifier.getNBridges()!=inverter.getNBridges())
            throw new IllegalArgumentException("Unequal bridge-count line preparation is not yet qualified");
        double current=rectifier.getIdc(),vr=rectifier.getDcVoltage(),vi=inverter.getDcVoltage(),
                resistance=owner.getRdc(UnitType.Ohm);
        if(!Double.isFinite(current)||current<=0||!Double.isFinite(vr)||vr<=0||!Double.isFinite(vi)||vi<=0
                ||!Double.isFinite(resistance)||resistance<=0||!Double.isFinite(inverter.getIdc())
                ||Math.abs(current-inverter.getIdc())>1e-7*Math.max(1,current)
                ||Math.abs(vr-vi-current*resistance)>1e-7*Math.max(vr,vi))
            throw new IllegalArgumentException("Positive consistent solved DC currents and ohmic voltage drop required");
        var rBoundary=boundary(rectifier,true,network.getBaseKva()*1000);
        var iBoundary=boundary(inverter,false,network.getBaseKva()*1000);
        double frequency=network.getFrequency();
        var line=parameters.prepareDcLine(resistance,rectifier.getNBridges(),rectifier.getCommutingZ().getImaginary(),
                inverter.getCommutingZ().getImaginary(),frequency,networkIntervalSeconds);
        var runtime=new Chvdc2CoupledRuntime(parameters,rBoundary,iBoundary,line,
                inverter.getXformerRatio(),inverter.getXformerTapSetting());
        var rVoltage=rb.getVoltage();var iVoltage=ib.getVoltage();
        runtime.setBoundary(rVoltage.getReal(),rVoltage.getImaginary(),iVoltage.getReal(),iVoltage.getImaginary(),current,0);
        runtime.initialize(vr,vi,initialTimeSeconds);
        return new Binding(owner,new OperatingPoint(vr,vi,current,initialTimeSeconds),networkIntervalSeconds,
                frequency,line.effectiveHenries(),line.capacitanceFarads(),runtime);
    }

    private static LccConverterBoundary boundary(ThyConverter<?> converter,boolean rectifier,double baseVA) {
        if(converter.getCommutingZ()==null||converter.getCommutingZ().getReal()!=0||converter.getCommutingCapacitor()!=0)
            throw new IllegalArgumentException("Converter resistance and capacitor compensation are not qualified");
        double rated=converter.getAcRatedVoltage();
        if(!Double.isFinite(rated)||rated<0)throw new IllegalArgumentException("Nonnegative converter primary voltage base required");
        double baseVolts=converter.getBus().getBaseVoltage()*(rated>0?rated:1);
        return new LccConverterBoundary(rectifier,converter.getNBridges(),baseVolts,converter.getXformerRatio(),
                converter.getXformerTapSetting(),converter.getCommutingZ().getImaginary(),baseVA);
    }
}
