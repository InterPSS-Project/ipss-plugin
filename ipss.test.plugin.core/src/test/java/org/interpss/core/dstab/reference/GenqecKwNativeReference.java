package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Exact native generator inputs and complete raw histories, not complete-model parity. */
public final class GenqecKwNativeReference {
    private GenqecKwNativeReference() {}
    private static String resource(String name,boolean zipped,String digest){
        try(var stream=GenqecKwNativeReference.class.getResourceAsStream("/genqec-kw-native/"+name+".b64")){
            if(stream==null)throw new IllegalStateException("Missing generator schema reference: "+name);
            byte[] bytes=Base64.getMimeDecoder().decode(stream.readAllBytes());
            if(zipped)try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=input.readAllBytes();}
            String text=new String(bytes,StandardCharsets.UTF_8);
            if(!WtgibffraNativeReference.digest(text).equals(digest))throw new IllegalStateException("Changed generator schema reference: "+name);
            return text;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public record Profile(String name,double step,int samples,int selector,String csvSha256,String inputSha256){
        public double[] replayBounds(){return switch(name){
                case "kw-zero-coarse" -> new double[]{9.4712e-06,5.6825e-05,0.0022369,0.0017234,0.00016283,0.0029264,0.019058};
                case "kw-zero-fine" -> new double[]{4.9579e-06,2.7536e-05,0.0011717,0.00089577,9.518e-05,0.001567,0.0099909};
                case "kw-one-coarse" -> new double[]{6.2348e-05,5.6825e-05,0.0022372,0.0017234,0.00016286,0.0029369,0.019071};
                case "kw-one-fine" -> new double[]{3.1193e-05,2.7536e-05,0.0011718,0.00089577,9.53e-05,0.0015712,0.0099964};
                case "kw-high-coarse" -> new double[]{6.2554e-05,5.6825e-05,0.0022371,0.0017234,0.00016291,0.0029316,0.019065};
                case "kw-high-fine" -> new double[]{3.1466e-05,2.7536e-05,0.0011718,0.00089577,9.5161e-05,0.001569,0.0099933};
                default -> throw new IllegalArgumentException("Unknown field-compensation profile: "+name);
        };}
        public String body(){return resource(name+".csv",true,csvSha256);}
        public String input(){return resource(name+".dyr",false,inputSha256);}
        public String network(){return resource("network.raw",false,"986c6b3c8201ff0c4581a60e48af3438a14c692c12e4f8b966f4f2c710daa02b");}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("kw-zero-coarse",0.0005,605,1,"e19a30aa88985952f9682c16f246c0928db1fe185b2b3ec9a3600e1757256372","08841c85f2ef199b963d267f17d1be90cc05db080ffdb1cbd63da37cb2206020"),
        new Profile("kw-zero-fine",0.00025,1205,1,"7d506966f17e4610d2417ac235ef50d68ded4263d97c0a99085c5e460e3e1d44","08841c85f2ef199b963d267f17d1be90cc05db080ffdb1cbd63da37cb2206020"),
        new Profile("kw-one-coarse",0.0005,605,1,"3f7fea6d701d621f1c8f548e911f914834cd70382d312248f3a05f8f908ba583","28df141137d2ed1bad34b29acbc654359ee4b35f16ba83e3e78955bec3d0d853"),
        new Profile("kw-one-fine",0.00025,1205,1,"0ee11fd63eca5b92cba7c8d9c33f25996ebb988e617e7f7479609646911586cb","28df141137d2ed1bad34b29acbc654359ee4b35f16ba83e3e78955bec3d0d853"),
        new Profile("kw-high-coarse",0.0005,605,1,"b8784f7dc75565e2bce5edcd717127f090bcb88f11e1b19913da1058b5803dda","6cc91eb72bf2df5a8f76ec24eca81b1a2c324c40adbd235ece2e24a7dbc01925"),
        new Profile("kw-high-fine",0.00025,1205,1,"efa4896a0307ae28f2190eef6ef41102cc726772b6da77ba16969ecfc064b5cd","6cc91eb72bf2df5a8f76ec24eca81b1a2c324c40adbd235ece2e24a7dbc01925")
    );}
}
