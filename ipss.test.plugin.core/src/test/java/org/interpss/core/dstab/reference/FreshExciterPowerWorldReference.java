package org.interpss.core.dstab.reference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.io.ByteArrayInputStream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Explicit fresh numerical fixtures; historical references are never overridden. */
public final class FreshExciterPowerWorldReference {
    private FreshExciterPowerWorldReference() {}
    private static final String CONTRACT_SHA256="e2aa3a15008affc65337b19af96fec69d9a098a51e26bc8ca4d3c67b8b63352a";

    private static byte[] resource(String name)throws IOException {
        try(var input=FreshExciterPowerWorldReference.class.getResourceAsStream(name)) {
            if(input==null)throw new IOException("Missing reference resource: "+name);
            return input.readAllBytes();
        }
    }

    private static String canonical(byte[] bytes) {
        return new String(bytes,StandardCharsets.UTF_8).replace("\r\n","\n").replace('\r','\n');
    }

    private static String digest(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}
    }

    private static void require(boolean condition,String message) {
        if(!condition)throw new IllegalArgumentException(message);
    }

    private static JsonObject contract(String model)throws IOException {
        String text=canonical(resource("/fresh-exciter-contracts.json"));
        require(digest(text).equals(CONTRACT_SHA256),"Changed fresh exciter contract");
        var catalog=JsonParser.parseString(text).getAsJsonObject();
        require(catalog.get("schema").getAsString().equals("fresh-exciter-numerical-projection-v1"),"Unknown contract schema");
        for(var item:catalog.getAsJsonArray("profiles")) {
            var profile=item.getAsJsonObject();
            if(profile.get("model").getAsString().equals(model))return profile;
        }
        throw new IllegalArgumentException("No explicit fresh reference for model: "+model);
    }

    public static EmbeddedTrajectoryReference reference(String model)throws IOException {
        var profile=contract(model);String body;
        byte[] compressed=Base64.getMimeDecoder().decode(resource("/fresh-exciter-numeric/"+model+".csv.b64"));
        try(var input=new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            body=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        require(digest(body).equals(profile.get("numeric_sha256").getAsString()),"Changed fresh numerical projection");
        var metadata=profile.getAsJsonArray("fields");var fields=new EmbeddedTrajectoryReference.Field[metadata.size()];
        for(int i=0;i<fields.length;i++) {
            var row=metadata.get(i).getAsJsonArray();require(row.size()==5,"Expected five field descriptors");
            fields[i]=new EmbeddedTrajectoryReference.Field(row.get(0).getAsString(),row.get(1).getAsString(),
                    row.get(2).getAsString(),row.get(3).getAsString(),row.get(4).getAsString());
        }
        double[][] rows=body.lines().map(line->Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new);
        require(rows.length==profile.get("samples").getAsInt(),"Missing fresh numerical samples");
        return EmbeddedTrajectoryReference.of(fields,rows);
    }

    /** Hash every actual input before staging any bytes; never use another model as fallback. */
    public static Path stageInputs(String model,Path source,Path staged)throws IOException {
        var inputs=contract(model).getAsJsonObject("running_inputs");List<Path> targets=new ArrayList<>();List<byte[]> bodies=new ArrayList<>();
        for(String role:List.of("raw","dyr")) {
            var item=inputs.getAsJsonObject(role);String name=Path.of(item.get("path").getAsString()).getFileName().toString();
            byte[] body=Files.readAllBytes(source.resolve(name));
            require(digest(canonical(body)).equals(item.get("sha256").getAsString()),"Running input differs from the pinned native profile: "+role);
            Path target=staged.resolve(name);require(!Files.exists(target),"Do not overwrite an existing staged input");
            targets.add(target);bodies.add(body);
        }
        Files.createDirectories(staged);
        for(int i=0;i<targets.size();i++)Files.write(targets.get(i),bodies.get(i),StandardOpenOption.CREATE_NEW);
        return staged;
    }

    /** Numerical trace columns follow the unchanged model-specific recorder and metric order. */
    public static void trace(String model,String referenceId,EmbeddedTrajectoryReference reference,List<double[]> rows)throws IOException {
        require(!rows.isEmpty(),"Empty actual trace");
        Path path=Path.of("target/fresh-exciter-cross-reference",model+"-"+referenceId+"-actual.csv");
        Files.createDirectories(path.getParent());StringBuilder text=new StringBuilder("time_s");
        for(int i=1;i<rows.get(0).length;i++)text.append(",metric_").append(i);
        text.append('\n');
        for(double[] row:rows) {
            for(int i=0;i<row.length;i++){if(i>0)text.append(',');text.append(row[i]);}
            text.append('\n');
        }
        Files.writeString(path,text);
        System.out.println(model+" reference="+referenceId+" full rows="+reference.samples().size());
    }

    /** Explicit private-export audit; no JVM-wide reference selection. */
    public static void main(String[] args)throws IOException {
        require(args.length==2,"Usage: FreshExciterPowerWorldReference model raw-export.csv");
        var profile=contract(args[0]);var raw=PowerWorldCsvRepeatability.trajectory(Path.of(args[1]),profile.get("source_sha256").getAsString());
        var projected=reference(args[0]);
        require(projected.fields().equals(raw.fields())&&projected.samples().size()==raw.samples().size(),"Raw metadata or sample count differs from projection");
        for(int i=0;i<raw.samples().size();i++) {
            var actual=raw.samples().get(i);var expected=projected.samples().get(i);
            require(expected.time()==actual.time()&&expected.occurrence()==actual.occurrence()&&Arrays.equals(expected.values(),actual.values()),"Raw projection differs at sample "+i);
        }
        System.out.println(args[0]+" exact projection: "+raw.samples().size()+" samples, "+raw.fields().size()+" channels; all event occurrences retained");
    }
}
