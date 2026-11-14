package org.interpss.dstab.control.exc.ieee.y1981.st1;

import java.util.*;
import com.interpss.dstab.controller.cml.annotate.util.CMLSymbolMapper;
import com.interpss.dstab.controller.cml.annotate.util.CMLSymbolMapper.FixedExpression;
import com.interpss.dstab.datatype.CMLVarEnum;
import com.interpss.dstab.datatype.CMLVarRec;
import com.interpss.dstab.datatype.ExpCalculator;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.controller.cml.ICMLMachineVoltageProvider;
import com.interpss.dstab.controller.cml.field.*;
import com.interpss.dstab.controller.cml.field.block.*;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType;
import com.interpss.dstab.controller.cml.field.adapt.CMLControlBlockAdapter;
import com.interpss.dstab.controller.cml.field.func.HighValueFunction;
import com.interpss.dstab.controller.cml.runtime.CmlBlockKernel;
import com.interpss.dstab.controller.cml.wrapper.*;

/** Experimental evaluator for the unmodified ST1 graph. Not a general CML replacement. */
final class St1DirectEvaluation {
    final IEEE1981ST1Exciter model;
    final boolean boundaryReuseEnabled;
    long boundaryReuseCount;
    private final boolean fixedScheduleRequested;
    private boolean fixedSchedule;
    long fixedSchedulePasses;
    private final boolean standardBoundaryGraph;
    private final int feedbackHistorySlot;
    final Node[] nodes = new Node[8];
    private final FixedExpression[] inputs = new FixedExpression[8];
    private final boolean[] scalarInputs = new boolean[8];
    private final int[] inputDependencies=new int[8];
    private final int[] dependentInputs=new int[8];
    private int dirtyInputs;
    private final double[] cachedInput=new double[8];
    long reusedInputs,evaluatedInputs;

    private final FixedExpression gateExpression;
    final Node[] ordered;
    final com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter[] localBlocks;
    final boolean[] localLimited;
    final int[] localIds;
    private boolean primitiveLocal;
    long primitiveLocalAdvanceCount;
    private final double[] localU=new double[8],localX=new double[8],localD=new double[8],
        localK=new double[8],localT=new double[8],localT1=new double[8];
    private final StaticBlockType[] localTypes=new StaticBlockType[8];
    private final org.interpss.numeric.datatype.LimitType[] localLimits=new org.interpss.numeric.datatype.LimitType[8];
    private boolean fixedInputs, fixedPss, voltageRead, pssRead;
    private double fixedVoltageValue, fixedPssValue;
    final Node[] controls, feedback;
    final double[] states = new double[8], rates = new double[8];
    final BaseFieldAnWrapper<?> gate;
    static final String[] NAMES = {"trDelayBlock", "gainBlock", "filterBlock", "filterBlock1",
        "kaDelayBlock", "washoutBlock", "rateFeedbackInputBlock", "gainCustomBlock"};

    St1DirectEvaluation(IEEE1981ST1Exciter model) {
        this(model, true);
    }

    St1DirectEvaluation(IEEE1981ST1Exciter model, boolean useScalarInputs) {
        this(model,useScalarInputs,!Boolean.getBoolean("ipss.experiment.disableSt1BoundaryReuse"),
                !Boolean.getBoolean("ipss.experiment.disableSt1FixedSchedule"));
    }
    St1DirectEvaluation(IEEE1981ST1Exciter model, boolean useScalarInputs, boolean boundaryReuse, boolean useFixedSchedule) {
        this.boundaryReuseEnabled=boundaryReuse;
        this.fixedScheduleRequested=useFixedSchedule;
        this.model = model;
        BaseFieldAnWrapper<?> foundGate = null;
        var sorted = new ArrayList<Node>();
        for (var wrapper : model.getFieldWrapperList()) {
            if (wrapper.getFieldName().equals("uelGate1Function")) { foundGate=wrapper; continue; }
            int id=Arrays.asList(NAMES).indexOf(wrapper.getFieldName());
            if (id < 0 || !(wrapper instanceof BlockFieldAnWrapper<?> block))
                throw new IllegalStateException("Unexpected ST1 field: " + wrapper.getFieldName());
            Node node=new Node(id,block); nodes[id]=node; sorted.add(node);
        }
        if (foundGate == null || foundGate.getField().getClass() != HighValueFunction.class)
            throw new IllegalStateException("Unexpected ST1 selector");
        gate=foundGate;
        var gateInputs=((HighValueFunction)gate.getField()).getVarRecList();
        standardBoundaryGraph=gateInputs.size()==2
            && gateInputs.get(0).getSymbolType()==CMLVarEnum.ControllerFieldOutput
            && "this.gainBlock.y".equals(gateInputs.get(0).getSymbolStr())
            && gateInputs.get(1).getSymbolType()==CMLVarEnum.ControllerFieldParameter
            && "this.vuelGate1".equals(gateInputs.get(1).getSymbolStr());
        for (Node node : nodes) Objects.requireNonNull(node);
        localBlocks=sorted.stream().filter(n->n.control)
            .map(n->(com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter)n.block)
            .toArray(com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter[]::new);
        localIds=sorted.stream().filter(n->n.control).mapToInt(n->n.id).toArray();
        int feedbackSlot=-1;
        for(int i=0;i<localIds.length;i++)if(localIds[i]==5)feedbackSlot=i;
        feedbackHistorySlot=feedbackSlot;
        localLimited=new boolean[localBlocks.length];
        for(int i=0;i<localBlocks.length;i++) {
            localLimited[i]=localBlocks[i].getType()==StaticBlockType.NonWindup;
            if(localLimited[i] && !(localBlocks[i] instanceof DelayControlBlock))
                throw new IllegalStateException("Unsupported local ST1 projection");
        }
        sorted.sort(Comparator.comparingInt(n -> n.wrapper.getInitOrder()));
        ordered=sorted.toArray(Node[]::new);
        controls=sorted.stream().filter(n->n.control).toArray(Node[]::new);
        feedback=sorted.stream().filter(n->!n.control&&n.wrapper.isFeedback()).toArray(Node[]::new);
        int[] expectedOrder={0,5,1,2,3,4,7,6};
        fixedSchedule=fixedScheduleRequested && ordered.length==8 && feedback.length==0;
        for(int i=0;i<ordered.length && fixedSchedule;i++) {
            int id=expectedOrder[i];
            fixedSchedule=ordered[i].id==id && nodes[id].control==(id==0||id==5||id==2||id==3||id==4);
        }
        Class<?>[] types={DelayControlBlock.class,GainBlock.class,FilterControlBlock.class,
            FilterControlBlock.class,DelayControlBlock.class,WashoutControlBlock.class};
        for(int i=0;i<types.length;i++)if(nodes[i].block.getClass()!=types[i])
            throw new IllegalStateException("Standard ST1 block required at "+i);

        try {
            for(Node node:nodes) {
                inputs[node.id]=new FixedExpression(node.wrapper.getInputExp(),model,this::bindSignal);
                scalarInputs[node.id]=useScalarInputs && model.getClass()==IEEE1981ST1Exciter.class
                    && St1InputEquations.matches(node.id,node.wrapper.getInputExp());
                int dependencies=0;
                for(var record:node.wrapper.getInputExp().getRecList())dependencies|=dependency(record);
                inputDependencies[node.id]=dependencies;
                if(dependencies>=0)for(int source=0;source<8;source++)
                    if((dependencies&(1<<source))!=0)dependentInputs[source]|=1<<node.id;
            }
            var expression=new ExpCalculator("this.uelGate1Function.y");
            CMLSymbolMapper.mapExpressionSymbolStr2Type(expression.getRecList());
            gateExpression=new FixedExpression(expression,model,this::bindSignal);
        } catch(InterpssException failure) { throw new IllegalStateException("Cannot compile ST1 equations",failure); }

    }

    double y(int id) {
        if(primitiveLocal && id<6)return primitiveY(id);
        return switch(id) {
            case 0,4 -> ((DelayControlBlock)nodes[id].block).getY();
            case 1 -> ((GainBlock)nodes[id].block).getY();
            case 2,3 -> ((FilterControlBlock)nodes[id].block).getY();
            case 5 -> ((WashoutControlBlock)nodes[id].block).getY();
            case 6,7 -> nodes[id].block.getY();
            default -> throw new IllegalArgumentException();
        };
    }
    double pss() {
        if(fixedInputs && fixedPss && pssRead)return fixedPssValue;
        double value=model.stabilizerSignal();
        if(fixedInputs && fixedPss){fixedPssValue=value;pssRead=true;}
        return value;
    }
    double gate() {
        return evaluateExpression(gateExpression);
    }
    double signal(String name) {
        return switch(name) {
            case "this.gainBlock.y" -> y(1);
            case "this.uelGate1Function.y" -> gate();
            case "this.kaDelayBlock.y" -> y(4);
            case "this.trDelayBlock.y" -> y(0);
            case "this.filterBlock.y" -> y(2);
            case "this.filterBlock1.y" -> y(3);
            case "this.washoutBlock.y" -> y(5);
            default -> throw new IllegalArgumentException(name);
        };
    }
    private java.util.function.DoubleSupplier bindSignal(CMLVarRec record) {
        if(record.getSymbolType()==CMLVarEnum.MachVt)return this::voltage;
        if(record.getSymbolType()==CMLVarEnum.PssVs)return this::pss;
        if(record.getSymbolType()==CMLVarEnum.ControllerFieldOutput) {
            int id=Arrays.asList(NAMES).indexOf(record.getPrimaryFieldName());
            if(id>=0)return ()->y(id);
        }
        return null;
    }
    double voltage() {
        if(fixedInputs && voltageRead)return fixedVoltageValue;
        var machine=model.getMachine();
        double value=machine instanceof ICMLMachineVoltageProvider p ? p.getCmlMachineVoltage()
            : model.getDStabBus().getVoltage().abs()/machine.getVMultiFactor();
        if(fixedInputs){fixedVoltageValue=value;voltageRead=true;}
        return value;
    }
    private double evaluateExpression(FixedExpression expression) {
        try { return expression.evaluate(); }
        catch(InterpssException failure) { throw new IllegalStateException("ST1 input evaluation failed",failure); }
    }
    private int dependency(CMLVarRec record) {
        return switch(record.getSymbolType()) {
            case Constant,ControllerFieldParameter,ControllerRefPoint,MachVt,PssVs -> 0;
            case ControllerFieldOutput -> {
                int id=Arrays.asList(NAMES).indexOf(record.getPrimaryFieldName());
                if(id>=0)yield 1<<id;
                if(record.getPrimaryFieldName().equals("uelGate1Function"))yield 1<<1;
                yield -1;
            }
            default -> -1;
        };
    }
    double input(int id) {
        if(!primitiveLocal || inputDependencies[id]<0)return evaluateInput(id);
        if((dirtyInputs&(1<<id))==0){reusedInputs++;return cachedInput[id];}
        evaluatedInputs++;double value=evaluateInput(id);
        cachedInput[id]=value;dirtyInputs&=~(1<<id);return value;
    }
    private double evaluateInput(int id) {
        return scalarInputs[id] ? St1InputEquations.evaluate(this,id) : evaluateExpression(inputs[id]);
    }
    private void outputChanged(int id) {
        dirtyInputs|=dependentInputs[id];
        // The exact feedback tap also reads the regulator directly for its pre-field limiter signal.
        if(id==4)dirtyInputs|=dependentInputs[6];
    }
    private void localInput(int id,double value) {
        if(Double.doubleToRawLongBits(localU[id])!=Double.doubleToRawLongBits(value))outputChanged(id);
        localU[id]=value;
    }
    private void localState(int id,double value) {
        if(Double.doubleToRawLongBits(localX[id])!=Double.doubleToRawLongBits(value))outputChanged(id);
        localX[id]=value;
    }
    private static final Class<?> FEEDBACK_TYPE=new IEEE1981ST1Exciter().rateFeedbackInputBlock.getClass();
    private static final Class<?> OUTPUT_TYPE=new IEEE1981ST1Exciter().gainCustomBlock.getClass();
    private boolean standardBoundary() {
        if(!standardBoundaryGraph || model.getClass()!=IEEE1981ST1Exciter.class || nodes[6].block.getClass()!=FEEDBACK_TYPE
                || nodes[7].block.getClass()!=OUTPUT_TYPE)return false;
        var machine=model.getMachine();Class<?> type=machine.getClass();
        if(type!=com.interpss.dstab.mach.impl.RoundRotorMachineImpl.class
                && type!=com.interpss.dstab.mach.impl.SalientPoleMachineImpl.class
                && type!=org.interpss.dstab.mach.IeeeVcRoundRotorMachine.class
                && type!=org.interpss.dstab.mach.IeeeVcSalientPoleMachine.class)return false;
        if(((org.eclipse.emf.ecore.impl.BasicEObjectImpl)machine).eNotificationRequired()
                || model.getDStabBus().getClass()!=com.interpss.dstab.impl.DStabBusImpl.class
                || ((org.eclipse.emf.ecore.impl.BasicEObjectImpl)model.getDStabBus()).eNotificationRequired())return false;
        for(boolean standard:scalarInputs)if(!standard)return false;
        for(var limit:localLimits)if(limit!=null && limit.getClass()!=org.interpss.numeric.datatype.LimitType.class
                && limit.getClass()!=com.interpss.dstab.datatype.LimitExpression.class)return false;
        return true;
    }
    double advanceLocalStore(double dt,int substeps,boolean discontinuity,boolean wasActive,
            boolean cachePss,double[] history,double[] start,double[] rates) {
        if(history.length!=localBlocks.length || start.length!=localBlocks.length || rates.length!=localBlocks.length)
            throw new IllegalArgumentException("ST1 state layout differs from its bound blocks");
        if(fixedInputs)throw new IllegalStateException("Nested ST1 local integration");
        fixedInputs=true;fixedPss=cachePss;voltageRead=false;pssRead=false;
        try {
            if(!Boolean.getBoolean("ipss.experiment.disableSt1PrimitiveLocal")
                    && (cachePss || model.getMachine().getStabilizer()==null)) {
                capturePrimitiveLocal();primitiveLocal=true;primitiveLocalAdvanceCount++;
            }
            double h=dt/substeps,field=model.getMachine().getEfd();
            // A dynamic regulator and an inactive, zero-state washout leave no
            // rate-feedback loop. The preceding end-of-substep evaluation already
            // prepared the next substep's inputs and derivatives. Active feedback
            // must repeat that pass to refresh history, even when Ta is positive.
            boolean reuseBoundary=primitiveLocal && localT[4]>0. && localK[5]==0.
                && localX[5]==0. && feedbackHistorySlot>=0
                && (!wasActive || history[feedbackHistorySlot]==0.)
                && boundaryReuseEnabled && standardBoundary();
            if(reuseBoundary)boundaryReuseCount+=substeps-1;
            for(int sub=0;sub<substeps;sub++) {
                if(sub==0 || !reuseBoundary)evaluate();
                for(int i=0;i<localBlocks.length;i++) {
                    var block=localBlocks[i];
                    start[i]=primitiveLocal?localX[localIds[i]]:block.getStateX();
                    rates[i]=primitiveLocal?localD[localIds[i]]:block.getDX_dt();
                    if(!wasActive)history[i]=rates[i];
                    if(sub==0 && discontinuity)rates[i]=.5*(rates[i]+history[i]);
                }
                wasActive=true;
                for(int i=0;i<localBlocks.length;i++) {
                    double trial=start[i]+h*(1.5*rates[i]-.5*history[i]);
                    double end=localLimited[i]?((DelayControlBlock)localBlocks[i]).getLimit().limit(trial):trial;
                    if(primitiveLocal)localState(localIds[i],end);else localBlocks[i].setStateX(end);
                    history[i]=end!=trial?0.:rates[i];
                }
                field=model.getStaggeredFieldVoltage();
                evaluate();
            }
            return field;
        } finally {
            if(primitiveLocal) {
                for(int i=0;i<localBlocks.length;i++) {
                    int id=localIds[i];localBlocks[i].restoreIntegrationState(localU[id],localX[id],localD[id]);
                }
            }
            primitiveLocal=false;fixedInputs=false;fixedPss=false;voltageRead=false;pssRead=false;
        }
    }
    private void capturePrimitiveLocal() {
        dirtyInputs=255;
        for(int id=0;id<6;id++) {
            var block=(com.interpss.dstab.controller.cml.field.adapt.CMLStaticBlockAdapter)nodes[id].block;
            localU[id]=block.getU();localTypes[id]=block.getType();
            if(block instanceof com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter b) {
                localX[id]=b.getStateX();localD[id]=b.getDX_dt();
            }
            if(block instanceof DelayControlBlock b) {
                localK[id]=b.getK();localT[id]=b.getT();localLimits[id]=b.getLimit();
            } else if(block instanceof FilterControlBlock b) {
                localK[id]=b.getK();localT[id]=b.getT2();localT1[id]=b.getT1();localLimits[id]=b.getLimit();
            } else if(block instanceof WashoutControlBlock b) {
                localK[id]=b.getK();localT[id]=b.getT();
            } else if(block instanceof GainBlock b) {
                localK[id]=b.getK();localLimits[id]=b.getLimit();
            }
        }
    }
    private double primitiveY(int id) {
        double result;
        if(id==0 || id==4) {
            result=localT[id]<=0.?localK[id]*localU[id]:localX[id];
            return localTypes[id]==StaticBlockType.Limit || localTypes[id]==StaticBlockType.NonWindup
                ?localLimits[id].limit(result):result;
        }
        if(id==1) {
            result=localU[id]*localK[id];
            return localTypes[id]==StaticBlockType.Limit?localLimits[id].limit(result):result;
        }
        if(id==2 || id==3) {
            result=localT[id]>0.?localX[id]+localK[id]*localT1[id]*localU[id]/localT[id]:localU[id]*localK[id];
            return localTypes[id]==StaticBlockType.Limit || localTypes[id]==StaticBlockType.NonWindup && localT[id]==0.
                ?localLimits[id].limit(result):result;
        }
        return localT[id]==0.?0.:localK[id]*localU[id]-localX[id];
    }
    private void advancePrimitiveZero(Node node) {
        int id=node.id;double u=node.input,x=localX[id],rate;
        if(id==0 || id==4) {
            if(localTypes[id]==StaticBlockType.NonWindup) {
                if(localLimits[id].isViolated(x))x=localLimits[id].limit(x);
                rate=localT[id]>0.?(localK[id]*u-x)/localT[id]:0.;
                if(rate>0. && x>=localLimits[id].getMax())rate=0.;
                if(rate<0. && x<=localLimits[id].getMin())rate=0.;
            } else rate=localT[id]>0.?(localK[id]*u-x)/localT[id]:0.;
        } else if(id==2 || id==3)
            rate=localT[id]>0.?(localK[id]*(1.-localT1[id]/localT[id])*u-x)/localT[id]:0.;
        else rate=localT[id]==0.?0.:(localK[id]*u-x)/localT[id];
        localInput(id,u);localState(id,x+rate*0.);localD[id]=rate;
    }
    void evaluate() {
        for(Node node:nodes)if(node.wrapper.getField()!=node.block)
            throw new IllegalStateException("ST1 structure changed during a frozen plan");
        prepare(true);
        for (Node node : controls) advanceZero(node);
        prepare(false);
        prepare(false);
    }
    private boolean wholePending;
    private double wholeDt;
    private int wholeCount;
    // Four boundary coordinates: sensed/compensated voltage, raw terminal
    // magnitude (field ceilings), exciter-base IFD, and PSS output. No machine
    // or stabilizer equation is reevaluated at an internal local stage.
    private final double[] wholeLeft=new double[8],wholeEndpoint=new double[8];
    private final double[][] wholeOrigin=new double[8][4],wholeRollback=new double[8][4];
    private boolean wholeInProgress;
    boolean isWholeModifiedEulerBusy(){return wholePending||wholeInProgress;}
    private static final StaticBlockType[] WHOLE_MODES={StaticBlockType.NoLimit,StaticBlockType.Limit,StaticBlockType.NoLimit,
        StaticBlockType.NoLimit,StaticBlockType.NonWindup,StaticBlockType.NoLimit};
    boolean hasPendingWholeModifiedEuler(){return wholePending;}

    void requireWholeModifiedEulerGraph() {
        if(model.getClass()!=IEEE1981ST1Exciter.class||!"ESST1A".equals(model.getName())||!standardBoundaryGraph
                ||nodes[6].block.getClass()!=FEEDBACK_TYPE||nodes[7].block.getClass()!=OUTPUT_TYPE
                ||model.getFieldWrapperList().size()!=9
                ||model.getData().getFieldVoltageLimitConvention()!=IEEE1981ST1ExciterData.FieldVoltageLimitConvention.ESST1A)
            throw new IllegalStateException("Whole local integration requires the exact standard ESST1A graph");
        for(int i=0;i<WHOLE_MODES.length;i++)if(((com.interpss.dstab.controller.cml.field.adapt.CMLStaticBlockAdapter)nodes[i].block).getType()!=WHOLE_MODES[i])
            throw new IllegalStateException("Unsupported ESST1A block limiter mode");
        for(Node node:nodes) {
            if(node.wrapper.getField()!=node.block || !St1InputEquations.matches(node.id,node.wrapper.getInputExp()))
                throw new IllegalStateException("Unsupported/changed ESST1A equation graph");
            if(node.block instanceof DelayControlBlock delay&&delay.getLocalIntegrationSteps()!=1)
                throw new IllegalStateException("Disable nested delay-block substeps before whole ESST1A integration");
        }
    }
    private void sampleWholeBoundary(double[] boundary) {
        boundary[0]=voltage();boundary[1]=model.observedTerminalMagnitude();
        boundary[2]=model.sampleLocalFieldCurrent();boundary[3]=pss();
        boundary[4]=model.getRefPoint();boundary[5]=model.vuel;boundary[6]=model.vuelGate1;boundary[7]=model.voel;
        for(int i=0;i<boundary.length;i++)if(Double.isNaN(boundary[i]) || i<6&&!Double.isFinite(boundary[i]))
            throw new IllegalArgumentException("Nonfinite ESST1A machine/reference/error input");
    }
    private void saveWholeBlocks(double[][] destination) {
        for(Node node:nodes) {
            double[] row=destination[node.id];row[0]=node.block.getU();row[3]=node.input;
            if(node.control) {
                var b=(com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter)node.block;
                row[1]=b.getStateX();row[2]=b.getDX_dt();
            }
        }
    }
    private void restoreWholeBlocks(double[][] source) {
        for(Node node:nodes) {
            double[] row=source[node.id];node.input=row[3];
            if(node.control)((com.interpss.dstab.controller.cml.field.adapt.CMLControlBlock1stOrderAdapter)node.block)
                    .restoreIntegrationState(row[0],row[1],row[2]);
            else node.block.eulerStep1(row[0],0.);
        }
    }
    private static double interpolateWhole(double left,double right,double fraction) {
        if(left==right)return left; // includes an unchanged absent-limit infinity
        if(!Double.isFinite(left)||!Double.isFinite(right))
            throw new IllegalArgumentException("ESST1A finite/infinite limiter transition requires an explicit event boundary");
        return fraction==1.?right:left+(right-left)*fraction;
    }
    private void restoreExternalBoundary(double[] endpoint) {
        model.setRefPoint(endpoint[4]);model.vuel=endpoint[5];model.vuelGate1=endpoint[6];model.voel=endpoint[7];
    }
    private void wholeBoundary(double[] right,double fraction) {
        fixedInputs=true;fixedPss=true;voltageRead=true;pssRead=true;
        fixedVoltageValue=wholeLeft[0]+(right[0]-wholeLeft[0])*fraction;
        fixedPssValue=wholeLeft[3]+(right[3]-wholeLeft[3])*fraction;
        model.prescribeLocalBoundary(wholeLeft[2]+(right[2]-wholeLeft[2])*fraction,
                wholeLeft[1]+(right[1]-wholeLeft[1])*fraction,fixedPssValue);
        model.setRefPoint(interpolateWhole(wholeLeft[4],right[4],fraction));
        model.vuel=interpolateWhole(wholeLeft[5],right[5],fraction);
        model.vuelGate1=interpolateWhole(wholeLeft[6],right[6],fraction);
        model.voel=interpolateWhole(wholeLeft[7],right[7],fraction);
    }
    Map<String,Object> wholeModifiedEulerSnapshot() {
        double[][] current=new double[8][4];saveWholeBlocks(current);
        var blocks=new LinkedHashMap<String,Object>();var origin=new LinkedHashMap<String,Object>();
        for(Node node:nodes) {
            blocks.put(NAMES[node.id],current[node.id].clone());
            if(wholePending)origin.put(NAMES[node.id],wholeOrigin[node.id].clone());
        }
        var result=new LinkedHashMap<String,Object>();
        result.put("pending",wholePending);result.put("blockCoordinateOrder",List.of("input","state","derivative","stagedInput"));
        result.put("blocks",Collections.unmodifiableMap(blocks));
        if(wholePending) {
            result.put("physicalStepSec",wholeDt);result.put("pendingSteps",wholeCount);
            result.put("boundaryCoordinateOrder",List.of("sensedVoltage","terminalMagnitude","exciterIfd","pss","reference","vuel","vuelGate1","voel"));
            result.put("acceptedBoundary",wholeLeft.clone());result.put("acceptedBlocks",Collections.unmodifiableMap(origin));
        }
        return result;
    }
    /** Each outer stage owns a complete local Heun integration. The corrector
     * starts at the same accepted graph origin and uses the selected input
     * sampling policy throughout the interval. Continuing the full predictor
     * would advance the states twice. Both modes retain the normal limiter and
     * preparation passes and refresh live inputs for zero-time event solves.
     */
    void wholeModifiedEulerStep(boolean predictor,double dt,int count) {
        requireWholeModifiedEulerGraph();
        if(!Double.isFinite(dt)||dt<0.||count<2||count>128)throw new IllegalArgumentException("Invalid ESST1A local interval/count");
        if(fixedInputs||primitiveLocal||wholeInProgress)throw new IllegalStateException("Nested ESST1A evaluator");
        if(dt>0.) {
            if(predictor==wholePending)throw new IllegalStateException(predictor?"Unfinished ESST1A predictor":"ESST1A corrector requires its predictor");
            if(!predictor&&(dt!=wholeDt||count!=wholeCount))throw new IllegalArgumentException("ESST1A corrector interval/count mismatch");
        }
        wholeInProgress=true;boolean boundarySampled=false,rollbackSaved=false;
        boolean callerPrescribed = model.hasPrescribedLocalBoundary();
        double[] endpoint=wholeEndpoint;
        boolean holdInputs = model.getModifiedEulerLocalInputSampling()
                == IEEE1981ST1Exciter.LocalInputSampling.HELD_AT_INTERVAL_START;
        try {
            sampleWholeBoundary(endpoint);boundarySampled=true;
            if (dt > 0. && !predictor && !holdInputs)
                for (int i = 4; i < 8; i++) interpolateWhole(wholeLeft[i], endpoint[i], .5);
            saveWholeBlocks(wholeRollback);rollbackSaved=true;
            if(dt>0.&&predictor) {
                System.arraycopy(endpoint,0,wholeLeft,0,8);saveWholeBlocks(wholeOrigin);wholeDt=dt;wholeCount=count;
            }
            if(dt==0.) {
                // Observe algebraic feedthrough only. Never project/integrate a
                // differential state or replace pending local derivative history.
                fixedInputs=true;fixedPss=true;voltageRead=true;pssRead=true;
                fixedVoltageValue=endpoint[0];fixedPssValue=endpoint[3];model.prescribeLocalBoundary(endpoint[2],endpoint[1],endpoint[3]);
                prepare(false);prepare(false);prepare(false);return;
            }
            if(!predictor)restoreWholeBlocks(wholeOrigin);
            double localDt=dt/count;
            double[] intervalEnd = predictor || holdInputs ? wholeLeft : endpoint;
            for(int i=0;i<count;i++) {
                wholeBoundary(intervalEnd,(double)i/count);
                modifiedEulerStep(true,localDt);
                wholeBoundary(intervalEnd,(double)(i+1)/count);
                modifiedEulerStep(false,localDt);
            }
            wholePending=predictor;
            if(!predictor){wholeDt=0.;wholeCount=0;}
        } catch(RuntimeException|Error failure) {
            if(rollbackSaved)restoreWholeBlocks(wholeRollback);throw failure;
        } finally {
            if (boundarySampled) {
                restoreExternalBoundary(endpoint);
                // Local interpolation owns a temporary override. Preserve the
                // caller's accepted endpoint for output and the next step.
                if (callerPrescribed) {
                    model.prescribeLocalBoundary(endpoint[2], endpoint[1], endpoint[3]);
                } else {
                    model.releaseLocalBoundary();
                }
            }
            fixedInputs=false;fixedPss=false;voltageRead=false;pssRead=false;wholeInProgress=false;
        }
    }

    /** Ordinary predictor/corrector stages; the caller owns the network endpoint. */
    void modifiedEulerStep(boolean predictor,double dt) {
        for(Node node:nodes)if(node.wrapper.getField()!=node.block)
            throw new IllegalStateException("ST1 structure changed; reinitialize the controller");
        prepare(true);
        for(Node node:controls)
            node.kernel.advance(predictor,node.input,dt,null,null,states,rates,node.id);
        prepare(false);
        prepare(false);
    }
    void prepare(boolean capture) {
        // Fixed call sites retain the ordered traversal and all three preparation
        // passes. Alternate graphs and ordinary evaluations keep the node loop.
        if(primitiveLocal && fixedSchedule) {
            fixedSchedulePasses++;
            prepareFixedControl(0,capture);
            prepareFixedControl(5,capture);
            prepareFixedStatic(1);
            prepareFixedControl(2,capture);
            prepareFixedControl(3,capture);
            prepareFixedControl(4,capture);
            prepareFixedStatic(7);
            prepareFixedStatic(6);
            return;
        }
        for (Node node : ordered) node.prepare(capture);
        for (Node node : feedback) node.prepare(false);
    }
    private void prepareFixedControl(int id,boolean capture) {
        double value=input(id);
        localInput(id,value);
        if(capture)nodes[id].input=value;
    }
    private void prepareFixedStatic(int id) {
        double value=input(id);
        var block=nodes[id].block;
        double previousInput=block.getU();
        block.eulerStep1(value,0.);
        if(id<6)localInput(id,value);
        else if(Double.doubleToRawLongBits(previousInput)!=Double.doubleToRawLongBits(block.getU()))outputChanged(id);
    }
    void advanceZero(Node node) {
        if(primitiveLocal){advancePrimitiveZero(node);return;}
        double input=node.input;
        if(node.id==0||node.id==4) {
            DelayControlBlock b=(DelayControlBlock)node.block;
            if(b.getType()==StaticBlockType.NonWindup||b.getLocalIntegrationSteps()>1) {
                node.kernel.advance(true,input,0.,null,null,states,rates,node.id);return;
            }
            double x=b.getStateX();
            double rate=b.getT()>0.?(b.getK()*input-x)/b.getT():0.;
            states[node.id]=x+rate*0.;rates[node.id]=rate;
            b.restoreIntegrationState(input,states[node.id],rate);
        } else if(node.id==2||node.id==3) {
            FilterControlBlock b=(FilterControlBlock)node.block;
            if(b.getType()==StaticBlockType.NonWindup) {
                node.kernel.advance(true,input,0.,null,null,states,rates,node.id);return;
            }
            double x=b.getStateX();
            double rate=b.getT2()>0.?(b.getK()*(1.-b.getT1()/b.getT2())*input-x)/b.getT2():0.;
            states[node.id]=x+rate*0.;rates[node.id]=rate;
            b.restoreIntegrationState(input,states[node.id],rate);
        } else if(node.id==5) {
            WashoutControlBlock b=(WashoutControlBlock)node.block;
            if(b.getType()==StaticBlockType.NonWindup) {
                node.kernel.advance(true,input,0.,null,null,states,rates,node.id);return;
            }
            double x=b.getStateX();
            double rate=b.getT()==0.?0.:(b.getK()*input-x)/b.getT();
            states[node.id]=x+rate*0.;rates[node.id]=rate;
            b.restoreIntegrationState(input,states[node.id],rate);
        } else throw new IllegalStateException("Unexpected ST1 control");
    }
    final class Node {
        final int id;
        final BlockFieldAnWrapper<?> wrapper;
        final boolean control;
        final ICMLStaticBlock block;
        final CmlBlockKernel kernel;
        double input;
        Node(int id,BlockFieldAnWrapper<?> wrapper) {
            this.id=id; this.wrapper=wrapper; block=(ICMLStaticBlock)wrapper.getField();
            control=wrapper instanceof ControlBlockFieldAnWrapper;
            kernel=CmlBlockKernel.create(block);
            if (block instanceof ICMLLimitBlock limited && limited.getLimit()!=null
                && (limited.getLimit().getMaxExp()!=null || limited.getLimit().getMinExp()!=null))
                throw new IllegalStateException("Variable limit requires general evaluator");
        }
        void prepare(boolean capture) {
            double value=St1DirectEvaluation.this.input(id);
            if (control) {
                if(primitiveLocal)localInput(id,value);
                else ((CMLControlBlockAdapter)block).setStageInput(value);
                if(capture) input=value;
            } else {
                double previousInput=primitiveLocal?block.getU():0.;
                block.eulerStep1(value,0.);
                if(primitiveLocal) {
                    if(id<6)localInput(id,value);
                    else if(Double.doubleToRawLongBits(previousInput)!=Double.doubleToRawLongBits(block.getU()))outputChanged(id);
                }
            }
        }
    }
}
