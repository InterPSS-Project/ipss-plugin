package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Complete native input-selector histories; unused voltage-derivative VARs are not physical channels. */
public final class IeeestInputReference {
    private IeeestInputReference() {}
    private static String resource(String name,int parts,boolean zipped,String digest) {
        try {
            var encoded=new ByteArrayOutputStream();
            for(int index=0;index<parts;index++)try(var stream=IeeestInputReference.class.getResourceAsStream("/ieeest-input/"+name+"."+index+".b64")) {
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
        new Profile("electrical-flat-coarse",0.0005,0.0005000000237487257,0.5,1005,3,false,"d5d1c7d5e32155282d3e65b94846df9fc90d85c7d8a6a8b7ecdffdcd1dfaefa2",1,"d0fe721732821e30c7c2381efbf0fed236c42a7b6a1321111b7c1f925270dad7",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("electrical-flat-fine",0.00025,0.0002500000118743628,0.5,2005,3,false,"0424a963b9bdcb99c22d0f23b67a73fb90f509c01e6dcf1cec9b83b0295c2068",1,"d0fe721732821e30c7c2381efbf0fed236c42a7b6a1321111b7c1f925270dad7",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("electrical-fault-coarse",0.0005,0.0005000000237487257,0.5,1005,3,true,"20a1abf6b0b26dce79911085ff1490a12b69287b17b84e53ee497e904a71ddbd",1,"d0fe721732821e30c7c2381efbf0fed236c42a7b6a1321111b7c1f925270dad7",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("electrical-fault-fine",0.00025,0.0002500000118743628,0.5,2005,3,true,"6a1ace3363a939747db20e3d5311f80f2e07a13cf11b3ca712efd39233e67ec1",2,"d0fe721732821e30c7c2381efbf0fed236c42a7b6a1321111b7c1f925270dad7",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("accelerating-flat-coarse",0.0005,0.0005000000237487257,0.5,1005,4,false,"5967f653c41c630ac20b2b811a42bbcae693617063e5070d28abdb940073ce32",1,"1afcd59080b6961a011ad1006057cdd4db6e78a69faaf7ca98cceb780f152a13",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("accelerating-flat-fine",0.00025,0.0002500000118743628,0.5,2005,4,false,"0a53b40721ebdd27e9ead2866b83a194ed70cff931948625733e0c49cb917ef4",1,"1afcd59080b6961a011ad1006057cdd4db6e78a69faaf7ca98cceb780f152a13",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("accelerating-fault-coarse",0.0005,0.0005000000237487257,0.5,1005,4,true,"e77f3b29c00f64af8aac3ee5a620f2fd8d5d29d225c638c446e37c7992456d67",1,"1afcd59080b6961a011ad1006057cdd4db6e78a69faaf7ca98cceb780f152a13",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("accelerating-fault-fine",0.00025,0.0002500000118743628,0.5,2005,4,true,"5bf425ba34ac76eda4b9802ccd45214829e57c802effc9620753920f848e5e86",2,"1afcd59080b6961a011ad1006057cdd4db6e78a69faaf7ca98cceb780f152a13",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1)
    );}
}
