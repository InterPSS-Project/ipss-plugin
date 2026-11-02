package org.interpss.dstab.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.SalientPoleMachine;
import com.interpss.dstab.mach.RoundRotorMachine;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernor;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter;
import org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter;
import org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor;
import org.interpss.dstab.renewable.Regca1Model;

/**
 * Non-mutating, pre-initialization checks for selected machine/controller families.
 * Time-step warnings screen individual poles; they do not certify coupled stability.
 * Uncovered devices remain explicit in the report and retain their own validation.
 */
public final class DynamicParameterValidator {
    private DynamicParameterValidator() { }
    public enum Severity { ERROR, WARNING, INFO }
    public record Issue(Severity severity, String owner, String model, String parameter,
            double value, double effectiveStepSeconds, boolean initiallyActive, String message) { }
    public record Report(List<Issue> issues, Map<String,Integer> checkedModels,
            Map<String,Integer> uncoveredModels) {
        public Report {
            issues=List.copyOf(issues);
            checkedModels=java.util.Collections.unmodifiableMap(new TreeMap<>(checkedModels));
            uncoveredModels=java.util.Collections.unmodifiableMap(new TreeMap<>(uncoveredModels));
        }
        public long count(Severity severity) {
            return issues.stream().filter(i->i.severity()==severity).count();
        }
        public void requireNoErrors() {
            if(count(Severity.ERROR)>0)
                throw new IllegalArgumentException("Dynamic parameter validation failed: "+count(Severity.ERROR)
                        +" error(s). "+issues.stream().filter(i->i.severity()==Severity.ERROR).limit(5).toList());
        }
    }

    /** Include attached inactive devices because they may be activated during a run. */
    public static Report validate(BaseDStabNetwork<?,?> network, double networkStep,
            double minimumTimeStepMultiple, ToIntFunction<Machine> st1Substeps) {
        if(!Double.isFinite(networkStep)||networkStep<=0
                ||!Double.isFinite(minimumTimeStepMultiple)||minimumTimeStepMultiple<=0)
            throw new IllegalArgumentException("Validation step and time-constant multiple must be finite and positive");
        java.util.Objects.requireNonNull(network);
        java.util.Objects.requireNonNull(st1Substeps);
        var issues=new ArrayList<Issue>();
        var checked=new TreeMap<String,Integer>();
        var uncovered=new TreeMap<String,Integer>();
        var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object,Boolean>());
        for(var bus:network.getBusList()) {
            for(var gen:bus.getContributeGenList()) {
                Object device=gen.getDynamicGenDevice();
                if(device==null)continue; // Static/netted generation is handled by network preprocessing.
                seen.add(device);
                if(!(device instanceof Machine m)) {
                    if(device instanceof Regca1Model converter) {
                        checkConverter(converter, bus.isActive()&&gen.isActive()&&converter.isActive(),
                                networkStep, minimumTimeStepMultiple, issues, checked, uncovered);
                        continue;
                    }
                    uncovered.merge(device.getClass().getSimpleName(),1,Integer::sum);continue;
                }
                boolean active=bus.isActive()&&gen.isActive()&&m.isActive();
                var machineCheck=new Check(issues,m.getId(),m.getClass().getSimpleName(),networkStep,minimumTimeStepMultiple,active);
                if(m instanceof SalientPoleMachine || m instanceof EConstMachine) {
                    checked.merge(machineCheck.model,1,Integer::sum);
                    machineCheck.machine(m);
                } else uncovered.merge(machineCheck.model,1,Integer::sum);
                for(var controller:m.getControllerList()) {
                    int count=controller instanceof IEEE1981ST1Exciter ? st1Substeps.applyAsInt(m) : 1;
                    if(count<1||count>128)throw new IllegalArgumentException("Local validation substeps must be between 1 and 128");
                    var c=new Check(issues,m.getId(),controller.getClass().getSimpleName(),networkStep/count,minimumTimeStepMultiple,active&&controller.isActive());
                    boolean supported=c.controller(controller);
                    (supported?checked:uncovered).merge(c.model,1,Integer::sum);
                }
            }
            for(var device:bus.getDynamicBusDeviceList())
                if(seen.add(device)) {
                    if(device instanceof Regca1Model converter)
                        checkConverter(converter, bus.isActive()&&converter.isActive()
                                &&converter.getParentGen()!=null&&converter.getParentGen().isActive(),
                                networkStep, minimumTimeStepMultiple, issues, checked, uncovered);
                    else uncovered.merge(device.getClass().getSimpleName(),1,Integer::sum);
                }
        }
        for(var branch:network.getBranchList())
            for(var device:branch.getDynamicBranchDeviceList())
                if(seen.add(device))uncovered.merge(device.getClass().getSimpleName(),1,Integer::sum);
        return new Report(issues,checked,uncovered);
    }

    private static void checkConverter(Regca1Model converter, boolean active, double step,
            double multiple, List<Issue> issues, Map<String,Integer> checked, Map<String,Integer> uncovered) {
        var check=new Check(issues,converter.getExtendedDeviceId(),"Regca1Model",step,multiple,active);
        var data=converter.getData();
        // The converter advances at the network step. Its electrical controller's
        // internal subdivisions and ST1 subdivisions do not reduce this interval.
        for(var component:data.getClass().getRecordComponents()) {
            if(component.getType()!=double.class)continue;
            try {check.finite(component.getName(),(double)component.getAccessor().invoke(data));}
            catch(ReflectiveOperationException e) {throw new IllegalStateException("Cannot inspect converter data",e);}
        }
        check.time("Tg",data.tg(),true);
        check.time("Tfltr",data.tfltr(),true);
        if(data.lvplsw()!=0&&data.lvplsw()!=1)
            check.add(Severity.WARNING,"Lvplsw",data.lvplsw(),"Expected selector 0 or 1; review the imported low-voltage control setting");
        if(converter.getParentGen()==null)
            check.add(Severity.ERROR,"parent generator",0,"Converter initialization requires a parent generator");
        if(converter.getActiveElectricalController()==null)
            check.add(Severity.ERROR,"electrical controller",0,"Converter initialization requires an electrical controller");
        checked.merge("Regca1Model.converter",1,Integer::sum);
        var electrical=converter.getActiveElectricalController();
        var wind=electrical==null?null:electrical.getWindControlStack();
        var torque=wind==null?null:wind.getTorqueController();
        if(torque!=null) {
            var c=new Check(issues,converter.getExtendedDeviceId(),"Wttqa1Model",step,multiple,active);
            var d=torque.getData();c.time("Tp",d.tp(),true);c.time("Twref",d.twref(),true);
            c.limits("Torque",d.teMin(),d.teMax());
            if(!d.hasIncreasingPowerPoints())c.add(Severity.WARNING,"power-speed points",d.p1(),
                    "Nonincreasing power points retained in native interval order; review the source curve. Points are not sorted or adjusted.");
            if(d.tFlag()==2)c.add(Severity.WARNING,"Tflag",2,
                    "Native-compatible nonzero selector retained: the power-control branch is active.");
            checked.merge("Wttqa1Model",1,Integer::sum);
        }
        // Do not claim that checking the converter validates its composed controls.
        uncovered.merge("Regca1Model.bundleControllers",1,Integer::sum);
    }

    private static final class Check {
        final List<Issue> issues;
        final String owner,model;
        final double h,multiple;
        final boolean active;
        Check(List<Issue> issues,String owner,String model,double h,double multiple,boolean active) {
            this.issues=issues;this.owner=owner;this.model=model;this.h=h;this.multiple=multiple;this.active=active;
        }
        void add(Severity severity,String name,double value,String message) {
            issues.add(new Issue(severity,owner,model,name,value,h,active,message));
        }
        boolean finite(String name,double value) {
            if(Double.isFinite(value))return true;
            add(Severity.ERROR,name,value,"Parameter must be finite");return false;
        }
        void positive(String name,double value,boolean zeroAllowed) {
            if(finite(name,value)&&(value<0||(!zeroAllowed&&value==0)))
                add(Severity.ERROR,name,value,zeroAllowed?"Parameter must be nonnegative":"Parameter must be positive");
        }
        void time(String name,double value,boolean zeroAllowed) {
            positive(name,value,zeroAllowed);
            if(Double.isFinite(value)&&value>0&&value/h<multiple)
                add(Severity.WARNING,name,value,"Time constant is below "+multiple+" times the effective step ("
                        +(multiple*h)+" s); review local substeps or a smaller network step. This is a screening threshold, not a stability proof.");
        }
        void leadLag(String numerator,double lead,String denominator,double lag) {
            positive(numerator,lead,true);time(denominator,lag,true);
            if(Double.isFinite(lead)&&lag==0&&lead!=0)
                add(Severity.ERROR,numerator+"/"+denominator,lead,"Nonzero lead with zero lag is an improper transfer function");
        }
        void limits(String name,double low,double high) {
            boolean a=finite(name+".min",low),b=finite(name+".max",high);
            if(a&&b&&low>high)
                add(Severity.WARNING,name,low,"Reversed limits: min="+low+", max="+high+". Existing model initialization may reorder them; review the source data.");
        }
        void order(String name,double low,double high,boolean strict) {
            if(Double.isFinite(low)&&Double.isFinite(high)&&(strict?low>=high:low>high))
                add(Severity.ERROR,name,low,"Required reactance ordering: lower "+(strict?"<":"<=")+" upper ("+high+")");
        }
        // Only scalar data getters are read, once before initialization, never in the time loop.
        void finiteData(Object data, String... positiveInfinityAllowed) {
            var getters=java.util.Arrays.stream(data.getClass().getMethods())
                    .filter(m->m.getName().startsWith("get")&&m.getParameterCount()==0&&m.getReturnType()==double.class)
                    .sorted(java.util.Comparator.comparing(java.lang.reflect.Method::getName)).toList();
            for(var getter:getters)try {
                String name=getter.getName().substring(3);
                double value=(double)getter.invoke(data);
                // Some model dialects explicitly use +infinity for an absent upper limit.
                if(value==Double.POSITIVE_INFINITY&&java.util.Arrays.asList(positiveInfinityAllowed).contains(name))continue;
                finite(name,value);
            }
            catch(ReflectiveOperationException e) { throw new IllegalStateException("Cannot inspect "+model+"."+getter.getName(),e); }
        }
        void machine(Machine m) {
            positive("H",m.getH(),false);positive("rating",m.getRating(),false);
            finite("D",m.getD());positive("Ra",m.getRa(),true);
            if(m instanceof EConstMachine e)positive("Xd'",e.getXd1(),false);
            if(m instanceof SalientPoleMachine s) {
                double xd=m.getMachData().getXd();
                positive("Xd",xd,false);positive("Xq",s.getXq(),false);positive("Xl",s.getXl(),true);
                positive("Xd'",s.getXd1(),false);positive("Xd''",s.getXd11(),false);positive("Xq''",s.getXq11(),false);
                order("Xl < Xd'",s.getXl(),s.getXd1(),true); // Difference appears in the implemented machine equations.
                order("Xd'' <= Xd'",s.getXd11(),s.getXd1(),false);
                order("Xd' <= Xd",s.getXd1(),xd,false);
                order("Xl <= Xd''",s.getXl(),s.getXd11(),false);
                order("Xl <= Xq''",s.getXl(),s.getXq11(),false);
                time("Td0'",s.getTd01(),false);time("Td0''",s.getTd011(),false);time("Tq0''",s.getTq011(),false);
                if(s instanceof RoundRotorMachine q) {
                    positive("Xq'",q.getXq1(),false);order("Xl < Xq'",q.getXl(),q.getXq1(),true);
                    order("Xq'' <= Xq'",q.getXq11(),q.getXq1(),false);order("Xq' <= Xq",q.getXq1(),q.getXq(),false);
                    // The documented salient branch removes the q-axis transient state.
                    boolean removedQTransient=q instanceof org.interpss.dstab.mach.GenqecMachine
                            && q.getTq01()==0. && q.getXq1()==q.getXq();
                    time("Tq0'",q.getTq01(),removedQTransient);
                }
            }
        }
        boolean controller(Object controller) {
            if(controller instanceof org.interpss.dstab.control.exc.psse.expic1.Expic1Exciter c) {
                var d=c.getData();
                time("Tr",d.tr(),true);time("Ta2",d.ta2(),true);time("Ta4",d.ta4(),true);
                time("Tf1",d.tf1(),false);time("Tf2",d.tf2(),true);time("Te",d.te(),true);
                positive("Ka",d.ka(),true);positive("Ta1",d.ta1(),true);
                positive("Ta3",d.ta3(),true);positive("Kf",d.kf(),true);positive("Kc",d.kc(),true);
                limits("PI",d.vr2(),d.vr1());limits("VR",d.vrmin(),d.vrmax());limits("E0",d.efdmin(),d.efdmax());
                add(Severity.INFO,"initialization policy",h,"Effective limits include the solved initial operating point; imported constants are unchanged");
                return true;
            }
            if(controller instanceof IEEE2005ST4BExciter c) {
                var d=c.getData();finiteData(d,"Vgmax");
                time("Tr",d.getTr(),true);time("Ta",d.getTa(),true);
                limits("Vr",d.getVrmin(),d.getVrmax());limits("Vm",d.getVmmin(),d.getVmmax());
                if(Math.abs(d.getKpr())<=1e-9&&Math.abs(d.getKir())<=1e-9)
                    add(Severity.WARNING,"Kpr/Kir",d.getKpr(),"Inactive outer PI path: existing initialization substitutes Kpr=40; review source gains");
                if(Math.abs(d.getKpm())<=1e-9&&Math.abs(d.getKim())<=1e-9)
                    add(Severity.WARNING,"Kpm/Kim",d.getKpm(),"Inactive inner PI path: existing initialization substitutes Kpm=1; review source gains");
                add(Severity.INFO,"initialization policy",h,"Existing model may adjust time constants, gains and initial limits; this report does not change imported data");
                return true;
            }
            if(controller instanceof Ieee1968Type1Exciter c) {
                var d=c.getData();finiteData(d);
                time("Tr",d.getTr(),true);time("Ta",d.getTa(),true);time("Te",d.getTe(),false);
                if(d.getKf()!=0)time("Tf",d.getTf(),false);else positive("Tf",d.getTf(),true);
                limits("Vr",d.getVrmin(),d.getVrmax());
                add(Severity.INFO,"initialization policy",h,"Existing model may adjust time constants, field resistance and initial limits; this report does not change imported data");
                return true;
            }
            if(controller instanceof IeeeSteamTCDRGovernor c) {
                var d=c.getData();finiteData(d);
                leadLag("T2",d.getT2(),"T1",d.getT1());time("T3",d.getT3(),false);
                time("T4",d.getTch(),true);time("T5",d.getTrh1(),true);
                time("T6",d.getTrh2(),true);time("T7",d.getTco(),true);
                limits("P",d.getPmin(),d.getPmax());limits("valve rate",d.getPdown(),d.getPup());
                if(d.getPup()<0||d.getPdown()>0)
                    add(Severity.WARNING,"valve rate sign",d.getPup(),"Opening rate should be nonnegative and closing rate nonpositive; existing initialization changes signs");
                double sum=d.getFvhp()+d.getFhp()+d.getFip()+d.getFlp();
                positive("shaft fraction sum",sum,false); // Initialization divides by this sum.
                if(Double.isFinite(sum)&&sum>1)
                    add(Severity.WARNING,"shaft fraction sum",sum,"Existing initialization normalizes shaft fractions whose sum exceeds one");
                add(Severity.INFO,"initialization policy",h,"Existing model may adjust time constants, fractions, rate limits and initial valve limits; this report does not change imported data");
                return true;
            }
            if(controller instanceof IEEE1981ST1Exciter c) {
                var d=c.getData();finiteData(d);
                time("Tr",d.getTr(),true);time("Ta",d.getTa(),true);
                leadLag("Tc",d.getTc(),"Tb",d.getTb());leadLag("Tc1",d.getTc1(),"Tb1",d.getTb1());
                if(d.getKf()!=0)time("Tf",d.getTf(),false);else positive("Tf",d.getTf(),true);
                limits("Vi",d.getVimin(),d.getVimax());limits("Va",d.getVamin(),d.getVamax());limits("Vr",d.getVrmin(),d.getVrmax());
                if(d.getKa()==0)add(Severity.WARNING,"Ka",0,"Review whether zero regulator gain can initialize the required field output");
                return true;
            }
            if(controller instanceof Ieee1992PSS2AStabilizer c) {
                var d=c.getData();finiteData(d);
                time("Tw1",d.getTw1(),true);time("Tw2",d.getTw2(),true);time("Tw3",d.getTw3(),true);time("Tw4",d.getTw4(),true);
                time("T6",d.getT6(),true);time("T7",d.getT7(),true);
                leadLag("T1",d.getT1(),"T2",d.getT2());leadLag("T3",d.getT3(),"T4",d.getT4());
                leadLag("Ta",d.getTa(),"Tb",d.getTb());
                if(d.getN()>0)leadLag("T8",d.getT8(),"T9",d.getT9());
                limits("Vst",d.getVstmin(),d.getVstmax());
                add(Severity.INFO,"initialization policy",h,"Existing model may adjust time constants and output limits during initialization; this report inspects imported values and makes no changes.");
                return true;
            }
            if(controller instanceof PsseTGov1SteamTurGovernor c) {
                var d=c.getData();finiteData(d);positive("R",d.getR(),false);
                time("T1",d.getT1(),true);leadLag("T2",d.getT2(),"T3",d.getT3());limits("V",d.getvMin(),d.getvMax());return true;
            }
            if(controller instanceof PsseHygovGovernor c) {
                var d=c.getData();finiteData(d);
                time("Tr",d.getTr(),false);time("Tf",d.getTf(),true);time("Tg",d.getTg(),false);time("Tw",d.getTw(),false);
                limits("G",d.getGmin(),d.getGmax());
                if(!c.validateParameters())add(Severity.ERROR,"model constraints",0,"Existing HYGOV parameter validation rejects this data");
                add(Severity.INFO,"initialization policy",h,"Existing governor may increase small time constants and expand initial limits; imported values are unchanged by this report.");return true;
            }
            if(controller instanceof org.interpss.dstab.control.gov.psse.hyg3.PsseHyg3Governor c) {
                var d=c.getData();finiteData(d);
                time("Tt",d.getTt(),true);time("Td",d.getTd(),true);time("Tp",d.getTp(),true);
                time("Tw",d.getTw(),true);time("Tf",d.getTf(),true);
                limits("gate",d.getPmin(),d.getPmax());limits("valve",d.getVelclose(),d.getVelopen());
                if(!c.validateParameters())add(Severity.ERROR,"model constraints",0,"HYG3 parameter validation rejects this data, including selectors, gate curves or physical limits");
                double tf=d.getTf();
                if(tf==0.&&d.getZeroDerivativeFilterConvention()==org.interpss.dstab.control.gov.psse.hyg3.PsseHyg3GovernorData.ZeroDerivativeFilterConvention.FOUR_OUTER_INTERVALS) {
                    tf=4*h;
                    add(Severity.INFO,"Tf preparation",tf,"Zero imported Tf uses four outer integration intervals; entered data remain unchanged");
                    time("effective Tf",tf,false);
                }
                if(d.getTw()==0.)add(Severity.WARNING,"Tw",0.,"Zero water time selects an algebraic runtime extension; independent source-model trajectory acceptance is unavailable for this setting");
                if(d.getControlFlag()==org.interpss.dstab.control.gov.psse.hyg3.PsseHyg3GovernorData.PID_CONTROL&&d.getTd()<=1e-9) {
                    double denominator=1.+d.getRgate()*(d.getK2()+(tf>1e-9?d.getK1()/tf:0.));
                    if(!Double.isFinite(denominator)||denominator==0.)
                        add(Severity.ERROR,"PID algebraic feedback denominator",denominator,"Bypassed input filter requires a finite, nonzero algebraic-feedback denominator");
                }
                // A frozen-integral input/derivative subsystem can be faster than
                // either entered lag. This screen is not a complete coupled-stability proof.
                if(d.getControlFlag()==org.interpss.dstab.control.gov.psse.hyg3.PsseHyg3GovernorData.PID_CONTROL
                        &&d.getTd()>0.&&tf>0.&&d.getRgate()>=0.&&d.getK1()>=0.&&d.getK2()>=0.) {
                    double derivativeGain=d.getK1()/tf;
                    double a=(1.+d.getRgate()*(d.getK2()+derivativeGain))/d.getTd();
                    double b=d.getRgate()*derivativeGain/d.getTd(), inverseTf=1./tf;
                    double fastestTime=2./(a+inverseTf+Math.hypot(a-inverseTf,2.*Math.sqrt(b*inverseTf)));
                    if(Double.isFinite(fastestTime)&&fastestTime>0.&&fastestTime/h<multiple)
                        add(Severity.WARNING,"PID derivative/input screening time",fastestTime,
                                "Frozen-integral input/derivative feedback is faster than the configured step margin; review the integration interval and internal-state response. This is not a complete coupled-stability proof.");
                }
                add(Severity.INFO,"initialization policy",h,"HYG3 prepares water/turbine coefficients and initial limits separately; validation does not initialize states or change imported data");
                return true;
            }
            if(controller instanceof org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1Governor c) {
                double[] d=c.getConstants();
                for(int i=0;i<d.length;i++)finite(org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1Governor.CONSTANT_NAMES.get(i),d[i]);
                for(int index:new int[]{1,8,11,14,17}) {
                    String name=org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1Governor.CONSTANT_NAMES.get(index);
                    if(d[index]>0.&&d[index]<=2.*h)
                        add(Severity.INFO,name+" preparation",d[index],"Lag is bypassed at initialization when its entered time is at most two outer intervals; entered constants remain unchanged");
                    else time(name,d[index],true);
                }
                time("T3",d[3],false);
                positive("primary turbine fraction sum",d[9]+d[12]+d[15]+d[18],false);
                add(Severity.INFO,"initialization policy",h,"WSIEG1 prepares lag bypasses and initial valve bounds before either integrator; the rate-limited servo time is not bypassed or floored");
                return true;
            }
            if(org.interpss.dstab.control.gov.wecc.wsieg1.Wsieg1Governor.isSecondaryShaft(controller)) {
                add(Severity.INFO,"shared turbine owner",h,"Passive secondary shaft has no independent states or time constants; its primary governor is validated separately");
                return true;
            }
            if(controller instanceof PsseGgov1Governor c) {
                var d=c.getData();finiteData(d);
                time("Tpelec",d.getTpelec(),true);time("Tact",d.getTact(),false);time("Tdgov",d.getTdgov(),true);
                leadLag("Tc",d.getTc(),"Tb",d.getTb());time("Ta",d.getTa(),true);time("Tsb",d.getTsb(),true);time("Tfload",d.getTfload(),true);
                limits("V",d.getVmin(),d.getVmax());limits("error",d.getMinerr(),d.getMaxerr());
                if(!c.validateParameters())add(Severity.ERROR,"model constraints",0,"Existing GGOV1 parameter validation rejects this data");
                add(Severity.INFO,"initialization policy",h,"Existing governor may adjust effective time constants, gains and limits; imported values are unchanged by this report.");return true;
            }
            return false;
        }
    }
}
