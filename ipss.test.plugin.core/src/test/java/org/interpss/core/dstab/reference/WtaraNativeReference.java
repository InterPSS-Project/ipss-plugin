package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.interpss.dstab.renewable.Wtara1Data;

/** Native algebraic boundary references with every history, event and driver-API row. */
public final class WtaraNativeReference {
    private WtaraNativeReference() {}
    private static String resource(String path,boolean zipped,String expected) {
        try(var stream=WtaraNativeReference.class.getResourceAsStream("/wtara-native/"+path+".b64")) {
            if(stream==null)throw new IllegalStateException("Missing aerodynamic fixture: "+path);
            byte[] bytes=Base64.getMimeDecoder().decode(stream.readAllBytes());
            if(zipped)try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=input.readAllBytes();}
            String text=new String(bytes,StandardCharsets.UTF_8).replace("\r\n","\n").replace('\r','\n');
            if(!WtgibffraNativeReference.digest(text).equals(expected))throw new IllegalStateException("Changed aerodynamic fixture: "+path);
            return text;
        }catch(IOException error){throw new UncheckedIOException(error);}
    }
    public record Profile(String name,double step,int samples,double ka,double theta0,String csvSha,String dyrSha,String rawSha) {
        public String body(){return resource(name+".csv",true,csvSha);}
        public String input(){return resource(name+".dyr",false,dyrSha);}
        public String network(){return resource("network.raw",false,rawSha);}
        public Wtara1Data data(){return new Wtara1Data(ka,theta0);}
        public boolean hasFault(){return name.startsWith("fault-");}
        public boolean hasPitchDriver(){return !name.startsWith("flat-")&&!hasFault();}
        public double[] pitchDriver(){return theta0!=0?new double[]{theta0+2,theta0-2,theta0}:name.startsWith("negative-")?new double[]{8,9,0}:new double[]{3,1,0};}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("flat-coarse",0.0005,1005,0.007,0.0,"034b6250b227e15df057b6d7f80692f9903650ef46c3f479c441f84621232be4","eb45608b7c4e899e0ad50ff3da7151ddbf7f9962df2a9af1d73da61ab18b930d","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("flat-fine",0.00025,2005,0.007,0.0,"50467392f52be8aa541e6d904187bd5b0425dd3877fdafc8fdd59866530602bc","eb45608b7c4e899e0ad50ff3da7151ddbf7f9962df2a9af1d73da61ab18b930d","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("fault-coarse",0.0005,1005,0.007,0.0,"1987d8bdf16c0cf73a4c3cebe9ba24f08954a83984baca70ba89db2fe341e4b4","eb45608b7c4e899e0ad50ff3da7151ddbf7f9962df2a9af1d73da61ab18b930d","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("fault-fine",0.00025,2005,0.007,0.0,"23f2b10a5f2a3fd1831c518b9fab7c5515e8b101bd40c218b720f8716518d450","eb45608b7c4e899e0ad50ff3da7151ddbf7f9962df2a9af1d73da61ab18b930d","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("pitch-coarse",0.0005,1008,0.007,0.0,"25d6e39ca13451909900352bf04a1f6e20813fe06a7ef1984b461a783bfc1c6c","eb45608b7c4e899e0ad50ff3da7151ddbf7f9962df2a9af1d73da61ab18b930d","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("pitch-fine",0.00025,2008,0.007,0.0,"0fd57cf6d42fa1fc72c58e128937f90452ed687c2d0bd01fba8dea12a30defed","eb45608b7c4e899e0ad50ff3da7151ddbf7f9962df2a9af1d73da61ab18b930d","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("nonzero-coarse",0.0005,1008,0.007,5.0,"a2b19c0779ac5c6800288b0db1867826228d92dce1a303358fa552b03451f1cb","b404b2ad3ca8f6f683c6a05051cbc3e79390264e76a53f9f04fd7d34d4bc09a2","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("nonzero-fine",0.00025,2008,0.007,5.0,"6d3ca8b9ecf375ba0e6e69b781d92d7adabaf8c1b96de7281d3b17ef46356247","b404b2ad3ca8f6f683c6a05051cbc3e79390264e76a53f9f04fd7d34d4bc09a2","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("gain-coarse",0.0005,1008,0.014,0.0,"db3623ea7040b26fceef9a5723484489ae2eb821684d4e206646456a6ee34197","bd4e18e3c05638ce0ddde34e2cfcb5dbf4ccf345f161e0a06e104390286beb59","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("gain-fine",0.00025,2008,0.014,0.0,"06c10784cf2b4be35ebccb93c046d4f921394df94a3e254a9a6a9ff5f43acc89","bd4e18e3c05638ce0ddde34e2cfcb5dbf4ccf345f161e0a06e104390286beb59","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("negative-coarse",0.0005,1008,0.007,0.0,"71139be258a465bc9e6206967917c4b7c212a24ae1edc5855a0dd5cb75c06f97","eb45608b7c4e899e0ad50ff3da7151ddbf7f9962df2a9af1d73da61ab18b930d","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("negative-fine",0.00025,2008,0.007,0.0,"6db070eabab44d4a0d1d307f58ded5eff69f7c89f12c595b96dc009ccc3b98a7","eb45608b7c4e899e0ad50ff3da7151ddbf7f9962df2a9af1d73da61ab18b930d","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("nonunit-coarse",0.0005,1008,0.007,5.0,"649f03f3a9ee52cc0a916705144c6096184da2a9f92e6da8e8e3c0f0c68a695a","f5473c19bb8aee7ff926b4e3da58143266b355cb96b7d6ed0feae20ce3c00ff0","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("nonunit-fine",0.00025,2008,0.007,5.0,"0f97ac96fe2b1cd8d0164e9e9d0c18c3fe5bc032a6bdb726296f3e3f5808b14a","f5473c19bb8aee7ff926b4e3da58143266b355cb96b7d6ed0feae20ce3c00ff0","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("zero-gain-coarse",0.0005,1008,0.0,0.0,"e8fb94cff617c8ecc3d7ace308f48bec885bc8b12a679bfa0a4de7778e43ad20","4882235ea0afd2d8780c6b246055cdcd65e110fb4aab323c47ef64ac561dfdd1","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("zero-gain-fine",0.00025,2008,0.0,0.0,"60a62e69d18c647949900ec1f662b5474006897e382d6695a67d7b50d1611feb","4882235ea0afd2d8780c6b246055cdcd65e110fb4aab323c47ef64ac561dfdd1","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35")
    );}
}
