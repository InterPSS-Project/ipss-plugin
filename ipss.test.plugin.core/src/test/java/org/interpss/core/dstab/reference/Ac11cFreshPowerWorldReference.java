package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Pinned numerical projection of a cold-paired export; the raw export stays private. */
public final class Ac11cFreshPowerWorldReference {
    private Ac11cFreshPowerWorldReference() {}
    public static final String SOURCE_SHA256="9cbc6dfe6f43c8dbb18a2288e631afde9fc51deef432437ce934130e7cb23fa1";

    /** Explicit private-export audit, independent of the always-active public fixture test. */
    public static void main(String[] args)throws IOException {
        if(args.length!=1)throw new IllegalArgumentException("Usage: Ac11cFreshPowerWorldReference raw-export.csv");
        var raw=PowerWorldCsvRepeatability.trajectory(Path.of(args[0]),SOURCE_SHA256);var projected=reference();
        if(!projected.fields().equals(raw.fields())||projected.samples().size()!=raw.samples().size())
            throw new IllegalArgumentException("Raw trajectory metadata or sample count differs from the numerical projection");
        for(int i=0;i<raw.samples().size();i++) {
            var expected=projected.samples().get(i);var actual=raw.samples().get(i);
            if(expected.time()!=actual.time()||expected.occurrence()!=actual.occurrence()||!Arrays.equals(expected.values(),actual.values()))
                throw new IllegalArgumentException("Raw trajectory differs from the numerical projection at sample "+i);
        }
        System.out.println("Exact AC11C projection: "+raw.samples().size()+" samples, "+raw.fields().size()+" channels; all event occurrences retained");
    }
    public static EmbeddedTrajectoryReference reference() {
        try(var input=Ac11cFreshPowerWorldReference.class.getResourceAsStream("/ac11c-fresh-numeric.csv.b64")) {
            if(input==null)throw new IllegalStateException("Missing fresh AC11C numeric reference");
            byte[] compressed=Base64.getMimeDecoder().decode(input.readAllBytes());String body;
            try(var stream=new GZIPInputStream(new ByteArrayInputStream(compressed))){body=new String(stream.readAllBytes(),StandardCharsets.UTF_8);}
            if(!WtgibffraNativeReference.digest(body).equals("e67e27eaa5db7a84c2a12cdf4d376cec5f52fd2fa17633fd38f7c49ef2a46360"))throw new IllegalStateException("Changed AC11C numerical projection");
            var fields=new EmbeddedTrajectoryReference.Field[]{
            new EmbeddedTrajectoryReference.Field("Bus","1","GENERATOR_100.0","TSVpu","V pu"),
            new EmbeddedTrajectoryReference.Field("Bus","2","INFINITE BUS_100.0","TSVpu","V pu"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSMW","MW"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSMvar","Mvar"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSMachineState:1","Machine State\\Angle"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSMachineState:2","Machine State\\Speed w"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSMachineState:3","Machine State\\Eqp"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSMachineState:4","Machine State\\PsiDp"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSMachineState:5","Machine State\\PsiQpp"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSMachineState:6","Machine State\\Edp"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSExciterState:1","Exciter State\\VE"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSExciterState:2","Exciter State\\Sensed Vt"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSExciterState:3","Exciter State\\PIKpaTia1"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSExciterState:4","Exciter State\\StateKbTb1"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSExciterState:5","Exciter State\\PIKpaTia2"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSExciterState:6","Exciter State\\StateKbTb3"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSExciterState:7","Exciter State\\PIKpuTpu1"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSExciterState:8","Exciter State\\StateKbTb2"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSExciterState:9","Exciter State\\PIKpoTio1"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSRotorAngle","Rotor Angle"),
            new EmbeddedTrajectoryReference.Field("Generator","1 1","GENERATOR_100.0 1","TSSpeed","Speed"),
            new EmbeddedTrajectoryReference.Field("Generator","2 1","INFINITE BUS_100.0 1","TSRotorAngle","Rotor Angle"),
            new EmbeddedTrajectoryReference.Field("Generator","2 1","INFINITE BUS_100.0 1","TSSpeed","Speed")
            };
            double[][] rows=body.lines().map(line->Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new);
            if(rows.length!=2003)throw new IllegalStateException("Missing AC11C samples");
            return EmbeddedTrajectoryReference.of(fields,rows);
        }catch(IOException exception){throw new UncheckedIOException(exception);}
    }
}
