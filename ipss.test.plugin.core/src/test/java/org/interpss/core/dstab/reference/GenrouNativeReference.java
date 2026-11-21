package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Exact round-rotor SMIB inputs and complete native raw histories. */
public final class GenrouNativeReference {
    private GenrouNativeReference() {}
    private static String resource(String name,boolean zipped,String digest) {
        try(var stream=GenrouNativeReference.class.getResourceAsStream("/genrou-native/"+name+".b64")) {
            if(stream==null)throw new IllegalStateException("Missing round-rotor reference: "+name);
            byte[] bytes=Base64.getMimeDecoder().decode(stream.readAllBytes());
            if(zipped)try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=input.readAllBytes();}
            String text=new String(bytes,StandardCharsets.UTF_8);
            if(!WtgibffraNativeReference.digest(text).equals(digest))throw new IllegalStateException("Changed round-rotor reference: "+name);
            return text;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public record Profile(String name,double step,int samples,boolean fault,String csvSha256,String inputSha256,String networkSha256,double[] stateBounds) {
        public String body(){return resource(name+".csv",true,csvSha256);}
        public String input(){return resource(name+".dyr",false,inputSha256);}
        public String network(){return resource(name+".raw",false,networkSha256);}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("flat-coarse",0.0005,1005,false,"b802b80ed4289ff8b0dbab2a89041ae68ef5093f299cb970d193177e53ec2100","541ea2a8881d3055b1b22506c3d0b6f1609565e5b4dc6c87cf653c2d7f45e79e","30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",new double[]{1.7000000000000001e-07,1.3e-07,1.8e-07,1.4e-07,3e-09,1.7000000000000001e-07}),
        new Profile("flat-fine",0.00025,2005,false,"461bc580a75469ca45e7e32edefef2b8c972be3cd78edec63822040d91806b26","541ea2a8881d3055b1b22506c3d0b6f1609565e5b4dc6c87cf653c2d7f45e79e","30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",new double[]{1.7000000000000001e-07,1.3e-07,1.8e-07,1.4e-07,3e-09,1.7000000000000001e-07}),
        new Profile("fault-coarse",0.0005,1005,true,"3e9613883c0e239cd823bf681e4e274fba62868d4ca037318af3f50bff1fd18f","541ea2a8881d3055b1b22506c3d0b6f1609565e5b4dc6c87cf653c2d7f45e79e","30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",new double[]{2.6e-05,4.9e-05,0.0007000000000000001,0.00028000000000000003,1.5e-06,2.8e-06}),
        new Profile("fault-fine",0.00025,2005,true,"a89a07f0452de477766e79f79f8625e0f2264b1bbefee9d244a390d5a6a1d750","541ea2a8881d3055b1b22506c3d0b6f1609565e5b4dc6c87cf653c2d7f45e79e","30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",new double[]{1.3e-05,2.4e-05,0.00036,0.00014000000000000001,7e-07,2.2e-06}),
        new Profile("damping-coarse",0.0005,1005,true,"a6ec0f4b859beb5e9f84e4e76ad41d9a3f3048cb76a7f61ecc4f8a55f8b4aaaf","fe84914e749cd85d68e14bda15127ddc1ae6a53557d49e2692fe185b420c2b1b","30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",new double[]{2.6e-05,4.9e-05,0.0007000000000000001,0.00028000000000000003,1.5e-06,1.6999999999999998e-06}),
        new Profile("damping-fine",0.00025,2005,true,"1745833e8a431dfb117a38cab561ec4cf62491f416dcd53e5d53cb82edeffe53","fe84914e749cd85d68e14bda15127ddc1ae6a53557d49e2692fe185b420c2b1b","30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",new double[]{1.3e-05,2.4e-05,0.00036,0.00014000000000000001,7e-07,1.4e-06}),
        new Profile("saturated-flat-coarse",0.0005,1005,false,"0acf7dacda5443e70d334adc4c736467c2eac9e357b14d7b0d3f3dd31b981dfe","b2bb0bae255fcb60eac39efa5fd5b8c15791494e30e15e870bd057dd3e9a4103","30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",new double[]{1.4e-07,1.6e-07,9.9e-08,1.4e-07,1.1e-10,2e-07}),
        new Profile("saturated-flat-fine",0.00025,2005,false,"34a4064b559c43c3290641ad4e1ec4421c504e8dfa94e78246c0970af466b436","b2bb0bae255fcb60eac39efa5fd5b8c15791494e30e15e870bd057dd3e9a4103","30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",new double[]{1.4e-07,1.6e-07,9.9e-08,1.4e-07,1.1e-10,2e-07}),
        new Profile("saturated-fault-coarse",0.0005,1005,true,"1cccd71859940d3dd1d5bb2d8a03e2d2a5b6f53d17e2e65cbcda7a0ef558d0da","b2bb0bae255fcb60eac39efa5fd5b8c15791494e30e15e870bd057dd3e9a4103","30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",new double[]{2.6e-05,4.4999999999999996e-05,0.00071,0.00026000000000000003,1.4e-06,1.2999999999999998e-06}),
        new Profile("saturated-fault-fine",0.00025,2005,true,"98f36a7fc53aed3d608c72dbc4ae596bb38b648cd31a3dae488591b9d818b33c","b2bb0bae255fcb60eac39efa5fd5b8c15791494e30e15e870bd057dd3e9a4103","30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",new double[]{1.3e-05,2.3e-05,0.00036,0.00013000000000000002,6.900000000000001e-07,8.4e-07}),
        new Profile("resistive-coarse",0.0005,1005,true,"06fe1af2e3eed09aa41fc8df2e1b229ae74d1a5c4380df978a7ccc6fe5aae568","541ea2a8881d3055b1b22506c3d0b6f1609565e5b4dc6c87cf653c2d7f45e79e","b201507391bff0c1eeebbe16c1e6de091ceebe9ea15eff4a38919f229f9d4975",new double[]{2.7e-05,4.2e-05,0.00071,0.00024000000000000003,5.4e-07,1.8e-06}),
        new Profile("resistive-fine",0.00025,2005,true,"1d90d3e78575935a9833b6f91869e64ed5d20939d10c1c5bd0b5315dbe10ffb8","541ea2a8881d3055b1b22506c3d0b6f1609565e5b4dc6c87cf653c2d7f45e79e","b201507391bff0c1eeebbe16c1e6de091ceebe9ea15eff4a38919f229f9d4975",new double[]{1.4e-05,2.1e-05,0.00037000000000000005,0.00012000000000000002,2.8e-07,1.9e-06}),
        new Profile("mva50-coarse",0.0005,1005,true,"182ca7999956efa01440b2a8c272974311db3f285eac0afefdfb387276f0a857","541ea2a8881d3055b1b22506c3d0b6f1609565e5b4dc6c87cf653c2d7f45e79e","4e2a896bf2e2285162ab4dc321b8b3375848499b021f9294e4108a696dcedc6a",new double[]{2.9e-05,0.00011,0.0007700000000000001,0.0006000000000000001,4.1e-06,2.4e-06}),
        new Profile("mva50-fine",0.00025,2005,true,"db22a34c7cb6b2b35cc6d2cb89034cd5537140cc48df90eee33a75fe487074fe","541ea2a8881d3055b1b22506c3d0b6f1609565e5b4dc6c87cf653c2d7f45e79e","4e2a896bf2e2285162ab4dc321b8b3375848499b021f9294e4108a696dcedc6a",new double[]{1.4999999999999999e-05,5.2e-05,0.00039000000000000005,0.00031,2.1e-06,3.2e-06}),
        new Profile("mva200-coarse",0.0005,1005,true,"d5249ac9d5e85e2308c1d7c095639afa208f5ebc9d33bf86272016abe89234c0","541ea2a8881d3055b1b22506c3d0b6f1609565e5b4dc6c87cf653c2d7f45e79e","96c1023541a791504f82d6251baa592339c5ca66b6d6e1e3cb3dd5c02589220a",new double[]{1.8999999999999998e-05,1.8e-05,0.0005,9.8e-05,4.1e-07,1.1e-06}),
        new Profile("mva200-fine",0.00025,2005,true,"5b4ae489993fac5e62dbb9f9f8cf0c458e7e85e1649f1f75c7a756c1046873df","541ea2a8881d3055b1b22506c3d0b6f1609565e5b4dc6c87cf653c2d7f45e79e","96c1023541a791504f82d6251baa592339c5ca66b6d6e1e3cb3dd5c02589220a",new double[]{1.2e-05,8.599999999999999e-06,0.00026000000000000003,4.9999999999999996e-05,2.1e-07,9.3e-07}),
        new Profile("exponential-flat-coarse",0.0005,1005,false,"e81bb45f603b14e20b4a6d069f1389d8f2030cf60f1b7fb332320744e5c0f1dc","ff1dd0d1438de8d007861ed98448744f0b4bc84160aab7d53c4a8bd232bdaca3","30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",new double[]{1.5e-07,1.3e-07,9.5e-08,1.4e-07,5.9e-09,1.9e-07}),
        new Profile("exponential-flat-fine",0.00025,2005,false,"01a43639482c07016a5fb5354b7f1c1cf655e0d0f4c8657032cd9fc01fd69c89","ff1dd0d1438de8d007861ed98448744f0b4bc84160aab7d53c4a8bd232bdaca3","30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",new double[]{1.5e-07,1.3e-07,9.5e-08,1.4e-07,5.9e-09,1.9e-07}),
        new Profile("exponential-fault-coarse",0.0005,1005,true,"5db7f2b8cb74abeed1732100890b5abd9026bc9cb539897a3698654af7893644","ff1dd0d1438de8d007861ed98448744f0b4bc84160aab7d53c4a8bd232bdaca3","30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",new double[]{2.6e-05,4.4999999999999996e-05,0.00071,0.00026000000000000003,1.4e-06,1.6e-06}),
        new Profile("exponential-fault-fine",0.00025,2005,true,"7c61ade7f0b30f7ad35a7611c3508daebd09bf12355dd74b01b8da4a8f999b81","ff1dd0d1438de8d007861ed98448744f0b4bc84160aab7d53c4a8bd232bdaca3","30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",new double[]{1.3e-05,2.3e-05,0.00036,0.00013000000000000002,6.900000000000001e-07,1.4e-06})
    );}
}
