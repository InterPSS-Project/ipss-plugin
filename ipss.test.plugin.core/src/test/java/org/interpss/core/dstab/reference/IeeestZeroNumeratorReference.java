package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Complete native input-selector histories; unused voltage-derivative VARs are not physical channels. */
public final class IeeestZeroNumeratorReference {
    private IeeestZeroNumeratorReference() {}
    private static String resource(String name,int parts,boolean zipped,String digest) {
        try {
            var encoded=new ByteArrayOutputStream();
            for(int index=0;index<parts;index++)try(var stream=IeeestZeroNumeratorReference.class.getResourceAsStream("/ieeest-zero-numerator/"+name+"."+index+".b64")) {
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
        new Profile("accelerating-flat-coarse",0.0005,0.0005000000237487257,0.5,1005,4,false,"5967f653c41c630ac20b2b811a42bbcae693617063e5070d28abdb940073ce32",1,"84ad3cffa6775441b7c47569db1b1f05f520e4de391cebb8fbbb884d4ebb214f",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("accelerating-flat-fine",0.00025,0.0002500000118743628,0.5,2005,4,false,"0a53b40721ebdd27e9ead2866b83a194ed70cff931948625733e0c49cb917ef4",1,"84ad3cffa6775441b7c47569db1b1f05f520e4de391cebb8fbbb884d4ebb214f",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("accelerating-fault-coarse",0.0005,0.0005000000237487257,0.5,1005,4,true,"2e6cdfcba920306f9bf7f916e0af018973e22a89112b1ce2e7895c5890bee8a0",1,"84ad3cffa6775441b7c47569db1b1f05f520e4de391cebb8fbbb884d4ebb214f",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("accelerating-fault-fine",0.00025,0.0002500000118743628,0.5,2005,4,true,"287ea7aa73b2c28c56d4e48a8b29b5dc3143175d469ecc0c5862827fe983dd5f",2,"84ad3cffa6775441b7c47569db1b1f05f520e4de391cebb8fbbb884d4ebb214f",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1)
    );}
}
