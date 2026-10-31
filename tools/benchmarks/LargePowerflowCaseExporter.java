package org.interpss.benchmark;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import com.google.gson.GsonBuilder;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.*;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.funcImpl.zeroz.util.ZeroZBranchNetUtil;
import com.interpss.core.sparse.solver.SparseEqnSolverProvider;
import org.interpss.fadapter.matpower.MatpowerDirectParser;
import org.interpss.fadapter.psse.PSSEDirectParser;

/** Export the initialized fixed-control nodal equations for a sparse numerical comparison. */
public final class LargePowerflowCaseExporter {
    public static void main(String[] args) throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Expected raw|matpower, input, new output directory");
        Locale.setDefault(Locale.ROOT);SparseEqnSolverProvider.useJavaKlu();
        Path output=Path.of(args[2]);Files.createDirectory(output);
        AclfNetwork net=switch(args[0]) {
            case "raw" -> new PSSEDirectParser().parse(args[1]);
            case "matpower" -> new MatpowerDirectParser().parse(args[1]);
            default -> throw new IllegalArgumentException(args[0]);
        };
        net.setPolarCoordinate(true);net.getBusLoadLowVoltConfig().setApplyVoltAdjust(false);
        var algorithm=LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
        algorithm.setLfMethod(AclfMethodType.NR);algorithm.setInitBusVoltage(false);
        algorithm.getDataCheckConfig().setAutoTurnLine2Xfr(true);
        algorithm.getDataCheckConfig().setTurnOffIslandBus(false);
        algorithm.getLfAdjAlgo().setApplyAdjustAlgo(false);algorithm.getMessageLog().setEnabled(false);
        if(!algorithm.initializeAclfNet())throw new IllegalStateException("Input initialization failed");
        var index=new IdentityHashMap<BaseAclfBus<?,?>,Integer>();
        for(var bus:net.getBusList())if(bus.isActive())index.put(bus,index.size());
        int references=0,pv=0,branches=0;double minimumZ=Double.POSITIVE_INFINITY;
        try(var buses=new PrintWriter(Files.newBufferedWriter(output.resolve("bus.csv")));
            var edges=new PrintWriter(Files.newBufferedWriter(output.resolve("branch.csv")))) {
            buses.println("index,original_id,type,base_kv,desired_vm,desired_va,saved_vm,saved_va,specified_p,specified_q,yii_real,yii_imag");
            for(var bus:net.getBusList())if(bus.isActive()) {
                var function=bus.getFunctionLoad();
                if(function!=null && (function.getP().getB()!=0 || function.getP().getC()!=0
                        || function.getQ().getB()!=0 || function.getQ().getC()!=0))
                    throw new IllegalArgumentException("Voltage-dependent function load: "+bus.getId());
                if(!(bus.getLoadCode()==AclfLoadCode.CONST_P || bus.getLoadCode()==AclfLoadCode.NON_LOAD
                        || bus.getLoadCode()==AclfLoadCode.ZIP && !bus.getContributeLoadList().isEmpty()))
                    throw new IllegalArgumentException("Requires constant-power bus loads: "+bus.getId()+" "+bus.getLoadCode()+" "+bus.getContributeLoadList().stream().map(l -> l.getCode()+" CP="+l.getLoadCP()+" CI="+l.getLoadCI()+" CZ="+l.getLoadCZ()).toList());
                for(var load:bus.getContributeLoadList())if(load.isActive() && !(load.getCode()==AclfLoadCode.CONST_P || load.getCode()==AclfLoadCode.NON_LOAD))
                    throw new IllegalArgumentException("Requires constant-power contributed loads: "+bus.getId());
                int type=bus.isSwing()?3:bus.isPV()?2:1;if(type==3)references++;if(type==2)pv++;
                var load=com.interpss.core.aclf.impl.func.bus.AclfBusLoadHelper.calLoadPQ(bus);
                var external=bus.getExternalPowerIntoNet();
                double p=bus.getGenP()-load.getReal()-(external==null?0:external.getReal());
                double q=bus.getGenQ()-load.getImaginary()-(external==null?0:external.getImaginary());
                var yii=bus.yii();
                buses.printf("%d,\"%s\",%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g%n",
                        index.get(bus),bus.getId().replace("\"","\"\""),type,bus.getBaseVoltage()/1000,
                        bus.getDesiredVoltMag(),bus.getDesiredVoltAng(),bus.getVoltageMag(),bus.getVoltageAng(),p,q,yii.getReal(),yii.getImaginary());
            }
            edges.println("from,to,yft_real,yft_imag,ytf_real,ytf_imag");
            for(var element:net.getBranchList())if(element.isActive()) {
                if(!(element instanceof AclfBranch branch))throw new IllegalArgumentException("Unsupported branch "+element.getId());
                if(ZeroZBranchNetUtil.isAugmentedZbrBranch(branch))throw new IllegalArgumentException("Augmented ZBR requires extra equations: "+branch.getId());
                minimumZ=Math.min(minimumZ,branch.getAdjustedZ().abs());
                if(branch.isGroundBranch())continue;
                Integer from=index.get(branch.getFromAclfBus()),to=index.get(branch.getToAclfBus());
                if(from==null||to==null)throw new IllegalArgumentException("Active branch connects inactive bus");
                var a=branch.yft();var b=branch.ytf();
                edges.printf("%d,%d,%.17g,%.17g,%.17g,%.17g%n",from,to,a.getReal(),a.getImaginary(),b.getReal(),b.getImaginary());branches++;
            }
        }
        var info=new LinkedHashMap<String,Object>();info.put("schema",1);info.put("input",Path.of(args[1]).toAbsolutePath().toString());
        info.put("original_buses",net.getNoBus());info.put("active_buses",index.size());info.put("reference_buses",references);
        info.put("pv_buses",pv);info.put("branches",branches);info.put("base_mva",net.getBaseKva()/1000);
        info.put("minimum_adjusted_impedance",minimumZ);info.put("adjustment_controls",false);info.put("low_voltage_load_adjustment",false);
        Files.writeString(output.resolve("model.json"),new GsonBuilder().setPrettyPrinting().create().toJson(info)+"\n");
        System.out.println("EXPORTED "+new GsonBuilder().create().toJson(info));
    }
}
