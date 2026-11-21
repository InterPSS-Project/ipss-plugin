package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.interpss.dstab.renewable.Repcgfmc1Data;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;

/** Immutable native hybrid-plant profiles. Recorded boundary inputs; not coupled network acceptance. */
public final class Repcgfmc1KernelNativeReference {
    private Repcgfmc1KernelNativeReference() {}
    public record Profile(String name,double step,int samples,String rawSha256,String inputSha256) {
        private byte[] resource(String suffix) throws IOException {
            try(var stream=Repcgfmc1KernelNativeReference.class.getResourceAsStream("/repcgfmc-kernel-native/"+name+"."+suffix+".b64")) {
                if(stream==null)throw new IllegalStateException("Missing hybrid-plant reference "+name);
                return Base64.getMimeDecoder().decode(stream.readAllBytes());
            }
        }
        public String body() {
            try(var stream=new GZIPInputStream(new ByteArrayInputStream(resource("csv")))) {
                String result=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
                if(!WtgibffraNativeReference.digest(result).equals(rawSha256))throw new IllegalStateException("Changed hybrid-plant CSV");
                return result;
            } catch(IOException e){throw new UncheckedIOException(e);}
        }
        public String input() {
            try {
                String result=new String(resource("dyr"),StandardCharsets.UTF_8);
                if(!WtgibffraNativeReference.digest(result).equals(inputSha256))throw new IllegalStateException("Changed hybrid-plant DYR");
                return result;
            } catch(IOException e){throw new UncheckedIOException(e);}
        }
        public Repcgfmc1Data parameters() {
            try {
                var r=PsseDyrRecordReader.read(new StringReader(input()),name).stream()
                        .filter(row->row.sourceModelName().equals("REPCGFMC1U")).findFirst().orElseThrow();
                double[] c=new double[54];for(int i=0;i<54;i++)c[i]=r.doubleParameter(13+i);
                return new Repcgfmc1Data(r.intParameter(6),r.intParameter(7),r.intParameter(8),r.parameter(9),
                        r.intParameter(10),r.intParameter(11),r.intParameter(12),c);
            } catch(IOException e){throw new UncheckedIOException(e);}
        }
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("reference-coarse",0.0005,608,"617d571202f82b28a42718ecc21745064e560b5c80d5598a22a6d240ce1cdcdc","849f9b829f56f0a3db63f4c8d1e11c4d6a804650db6a48b0162997b73cbd8bcc"),
        new Profile("reference-fine",0.00025,1208,"540420a809b205235c118908a3f1eee622c7f1e87ce167685ade66efcbe92656","849f9b829f56f0a3db63f4c8d1e11c4d6a804650db6a48b0162997b73cbd8bcc"),
        new Profile("limits-coarse",0.0005,608,"8378d1eee1e0958e7f7060e8c22bd58b06a0ba2b798c3378a833605a1828daea","affa225d8359878788a4db104d8cce6d8b212f949a1f01b3b137ab7f46f8a30e"),
        new Profile("limits-fine",0.00025,1208,"73634ec3cfafb150585e5fdf3146e3707ff3c02132a88e1a2ad99f25915b4837","affa225d8359878788a4db104d8cce6d8b212f949a1f01b3b137ab7f46f8a30e"),
        new Profile("voltage-coarse",0.0005,608,"3878d8d6249a9ea8bfb3def181b595b4d4dc858a49e854e2f79a203a0e3638f5","18a532b2883548e7ee130d0da23053b4754497689ecfbd0f651cecd61c1c6939"),
        new Profile("voltage-fine",0.00025,1208,"07e907ebf816960cf578c209a780986ed8d7061fdddea1525bd8d92f01f5b0f2","18a532b2883548e7ee130d0da23053b4754497689ecfbd0f651cecd61c1c6939"),
        new Profile("ffr-coarse",0.0005,608,"88914adde0f0522846e855e70817bfb0ccc5bd47a99429e81329a201c60cbbdd","5b0a7f8423f787f17cacf8c1d093cfa5bb1b75c2b3ced4222f24ff283332f926"),
        new Profile("ffr-fine",0.00025,1208,"76c505dd3c896f72d6f46777393e15e5ed16004ce63538ced7dc2c273c9142b7","5b0a7f8423f787f17cacf8c1d093cfa5bb1b75c2b3ced4222f24ff283332f926"),
        new Profile("voltage-limits-coarse",0.0005,608,"46cc9a53dbec5ad84b44dd46cade6166b7846f15ba3a64d8d4e5913fe9c4536f","7cbcdadc328ed0e7e1b3b49246d9df8107e6ebbfb2a3adb4b38d37a24b2fadfe"),
        new Profile("voltage-limits-fine",0.00025,1208,"d86e9e79f19b4757c2b26e50c7485342b90819e75313e69bfa2378588a8521b9","7cbcdadc328ed0e7e1b3b49246d9df8107e6ebbfb2a3adb4b38d37a24b2fadfe")
    );}
}
