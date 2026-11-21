package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Full cold-paired numerical fixtures; controller replay and coupled evidence stay separate. */
public final class Tgov1LimitPowerWorldReference {
    private Tgov1LimitPowerWorldReference() {}
    public record Profile(String name,double step,int samples,String numericSha256,String rawSha256) {
        public double increment(){return name.startsWith("upper-")?.1:-.1;}
        public EmbeddedTrajectoryReference reference() {
            try(var stream=Tgov1LimitPowerWorldReference.class.getResourceAsStream("/tgov1-limit-powerworld/"+name+".csv.b64")) {
                if(stream==null)throw new IllegalArgumentException("Missing TGOV1 independent profile: "+name);
                byte[] compressed=Base64.getMimeDecoder().decode(stream.readAllBytes());String body;
                try(var input=new GZIPInputStream(new ByteArrayInputStream(compressed))){body=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
                if(!WtgibffraNativeReference.digest(body).equals(numericSha256))throw new IllegalStateException("Changed TGOV1 independent projection");
                double[][] rows=body.lines().map(line->Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new);
                if(rows.length!=samples)throw new IllegalStateException("Missing TGOV1 independent samples");
                return EmbeddedTrajectoryReference.of(fields(),rows);
            }catch(IOException exception){throw new UncheckedIOException(exception);}
        }
    }
    private static EmbeddedTrajectoryReference.Field[] fields(){return new EmbeddedTrajectoryReference.Field[]{
            new EmbeddedTrajectoryReference.Field("Bus","1","GENERATOR_100.0","TSVpu","V pu"),
            new EmbeddedTrajectoryReference.Field("Bus","2","INFINITE BUS_100.0","TSVpu","V pu"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSMW","MW"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSMvar","Mvar"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSSpeed","Speed"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSRotorAngle","Rotor Angle"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSGovernorState:1","Governor State\\Turbine Power"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSGovernorState:2","Governor State\\Valve Position"),
            new EmbeddedTrajectoryReference.Field("Generator","2 1","INFINITE BUS_100.0 1","TSSpeed","Speed"),
            new EmbeddedTrajectoryReference.Field("Generator","2 1","INFINITE BUS_100.0 1","TSRotorAngle","Rotor Angle"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSMachineState:3","Machine State\\Eqp"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSMachineState:4","Machine State\\PsiDp"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSMachineState:5","Machine State\\PsiQpp"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSMachineState:6","Machine State\\Edp")
    };}
    public static List<Profile> profiles(){return List.of(
        new Profile("upper-coarse",0.0005,1003,"be3d78df8c725cfc7a38037de5ca1e944fe36c77ad45c9f4191548f4b26eb8e6","fc590546ac69b9a48a378a23386227f2815f05d0ec698c46526dc5de23493357"),
        new Profile("upper-fine",0.00025,2003,"375303745bd2e28c817a413e17f35b99994a93072bbcef13b2d12fab67ebc374","a854c38d836886b8049af8719660922892f19b40956cd4743a8a5d48d2fceddf"),
        new Profile("lower-coarse",0.0005,1003,"813a41f0676563bb26d985761bb4069c03b2fcec07818aee6f486a9157bdeff1","0c3318af0e5bc6067ab4e859b4044bb7d1da704c7f90ca8efe57d17a5af62756"),
        new Profile("lower-fine",0.00025,2003,"51f059af8cd6a3065c706b34a6dfbdd921345184d9dad10a4a0eaec6357861f6","1ec252a6d3f2ebbb53d3c71c397f75d45c4ba29263b1b3536686b25c7983910a")
    );}
    public static void main(String[] args)throws IOException {
        if(args.length!=2)throw new IllegalArgumentException("Usage: Tgov1LimitPowerWorldReference profile raw-export.csv");
        var p=profiles().stream().filter(item->item.name().equals(args[0])).findFirst().orElseThrow();
        var raw=PowerWorldCsvRepeatability.trajectory(Path.of(args[1]),p.rawSha256());var projected=p.reference();
        if(!raw.fields().equals(projected.fields())||raw.samples().size()!=p.samples())throw new IllegalStateException("Changed raw metadata/count");
        for(int i=0;i<p.samples();i++) {
            var a=raw.samples().get(i);var b=projected.samples().get(i);
            if(a.time()!=b.time()||a.occurrence()!=b.occurrence()||!Arrays.equals(a.values(),b.values()))throw new IllegalStateException("Changed raw value at row "+i);
        }
        System.out.println(p.name()+" exact projection: "+p.samples()+" samples, 14 channels; all event occurrences retained");
    }
}
