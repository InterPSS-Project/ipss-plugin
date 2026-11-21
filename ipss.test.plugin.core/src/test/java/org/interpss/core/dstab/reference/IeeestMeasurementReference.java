package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Complete native input-selector histories; unused voltage-derivative VARs are not physical channels. */
public final class IeeestMeasurementReference {
    private IeeestMeasurementReference() {}
    private static String resource(String name,int parts,boolean zipped,String digest) {
        try {
            var encoded=new ByteArrayOutputStream();
            for(int index=0;index<parts;index++)try(var stream=IeeestMeasurementReference.class.getResourceAsStream("/ieeest-measurement/"+name+"."+index+".b64")) {
                if(stream==null)throw new IllegalStateException("Missing input reference: "+name+" part "+index);
                encoded.writeBytes(stream.readAllBytes());
            }
            byte[] bytes=Base64.getMimeDecoder().decode(encoded.toByteArray());
            if(zipped)try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=NativeFloatCsvCodec.decode(input.readAllBytes()).getBytes(StandardCharsets.UTF_8);}
            String text=new String(bytes,StandardCharsets.UTF_8);
            if(!WtgibffraNativeReference.digest(text).equals(digest))throw new IllegalStateException("Changed input reference: "+name);
            return text;
        }catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    public record Profile(String name,double step,double nativeStep,double end,int samples,int mode,boolean fault,String csvSha256,int csvParts,String inputSha256,int inputParts,String networkSha256,int networkParts) {
        public String body(){return resource(name+".csv",csvParts,true,csvSha256);}
        public String input(){return resource(name+".dyr",inputParts,false,inputSha256);}
        public String network(){return resource(name+".raw",networkParts,false,networkSha256);}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("frequency-local-flat-coarse",0.0005,0.0005000000237487257,0.5,1005,2,false,"dfd9940ab10c599cc782e05d13a86d2f6084f72a2e7cdc27023d547b0fde2833",1,"5d8ddae242ecae30b354455a1cf0fe512be67023e0f822a1a0f83c336f34f0ff",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("frequency-local-flat-fine",0.00025,0.0002500000118743628,0.5,2005,2,false,"dee15d7bb13447d08025167d39c9a38be91fc992674b7f7f83455af73d757c9a",1,"5d8ddae242ecae30b354455a1cf0fe512be67023e0f822a1a0f83c336f34f0ff",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("frequency-local-fault-coarse",0.0005,0.0005000000237487257,0.5,1005,2,true,"0f4fdb25d5c8e47feddb7ee2428fedd1a747541361fadaae7041841bbc957bd0",2,"5d8ddae242ecae30b354455a1cf0fe512be67023e0f822a1a0f83c336f34f0ff",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("frequency-local-fault-fine",0.00025,0.0002500000118743628,0.5,2005,2,true,"418a5ae4a5a34a30fba9afeed8d2b2a426eee9493b7a1c2aedb81f1938f33c1b",3,"5d8ddae242ecae30b354455a1cf0fe512be67023e0f822a1a0f83c336f34f0ff",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("frequency-remote-flat-coarse",0.0005,0.0005000000237487257,0.5,1005,2,false,"8dbd0dc49038fa1d0fc8954675da9a85b72fdd2c18b15358fa0757ed707e84ee",1,"2df647704083cfee8af232384d4b92de4df1d6573083fb9efab57f8fc223d0da",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("frequency-remote-flat-fine",0.00025,0.0002500000118743628,0.5,2005,2,false,"bc6be24b2a82d80cdc87a75c29add1014828784e2a6fad149284a804ad1dcc79",1,"2df647704083cfee8af232384d4b92de4df1d6573083fb9efab57f8fc223d0da",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("frequency-remote-fault-coarse",0.0005,0.0005000000237487257,0.5,1005,2,true,"16aa722034d0caa1fe877592fb78bf5f05f1fc5c4e384d9fcbbeba1c00793abc",2,"2df647704083cfee8af232384d4b92de4df1d6573083fb9efab57f8fc223d0da",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("frequency-remote-fault-fine",0.00025,0.0002500000118743628,0.5,2005,2,true,"1f6060b600e74a52d7aba9f43d4717b3163ae90feebe25aaa3ace7c6c172698b",3,"2df647704083cfee8af232384d4b92de4df1d6573083fb9efab57f8fc223d0da",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("voltage-local-flat-coarse",0.0005,0.0005000000237487257,0.5,1005,5,false,"d9746a40f9e9fd45a0c48ed4a9d9596e5e5a0d23678d9d0866840ab8f9bbd227",1,"1f86f1a6181556ecb33d3373053e81c587f6c84dfb474507c9e6967ab8c1f5d9",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("voltage-local-flat-fine",0.00025,0.0002500000118743628,0.5,2005,5,false,"eb2fe4265c1b612994e25e58eb9a2a8033fe03325a54dc4eb05eb3725fc07315",1,"1f86f1a6181556ecb33d3373053e81c587f6c84dfb474507c9e6967ab8c1f5d9",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("voltage-local-fault-coarse",0.0005,0.0005000000237487257,0.5,1005,5,true,"b92e216fe483f31864304d006713ee57277c3b289ed065d11563df5222085a74",1,"1f86f1a6181556ecb33d3373053e81c587f6c84dfb474507c9e6967ab8c1f5d9",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("voltage-local-fault-fine",0.00025,0.0002500000118743628,0.5,2005,5,true,"fa55c1cf2686feee453b09e7395c442a3eb44a73770b0038e9150d8c62a6dcb5",2,"1f86f1a6181556ecb33d3373053e81c587f6c84dfb474507c9e6967ab8c1f5d9",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("voltage-remote-flat-coarse",0.0005,0.0005000000237487257,0.5,1005,5,false,"d9746a40f9e9fd45a0c48ed4a9d9596e5e5a0d23678d9d0866840ab8f9bbd227",1,"a8c7d14da68db090cc586e330956e6a35453d76654e6f4532dc8c012685d7bd8",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("voltage-remote-flat-fine",0.00025,0.0002500000118743628,0.5,2005,5,false,"eb2fe4265c1b612994e25e58eb9a2a8033fe03325a54dc4eb05eb3725fc07315",1,"a8c7d14da68db090cc586e330956e6a35453d76654e6f4532dc8c012685d7bd8",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("voltage-remote-fault-coarse",0.0005,0.0005000000237487257,0.5,1005,5,true,"edc1c1a5a199f9ae6727e52297abdb7e98e0b482df352d6f478d2f364e27d36e",1,"a8c7d14da68db090cc586e330956e6a35453d76654e6f4532dc8c012685d7bd8",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("voltage-remote-fault-fine",0.00025,0.0002500000118743628,0.5,2005,5,true,"e5f88a75c00e14b03b2002e8ed5eb293f19e8b8c99bd6fee68e03bcee0fa5186",2,"a8c7d14da68db090cc586e330956e6a35453d76654e6f4532dc8c012685d7bd8",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1)
    );}
}
