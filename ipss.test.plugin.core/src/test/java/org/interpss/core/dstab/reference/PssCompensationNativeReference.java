package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Complete cold-paired non-unit-base histories, not complete model parity. */
public final class PssCompensationNativeReference {
    private PssCompensationNativeReference() {}
    private static String resource(String name,boolean zipped,String digest){
        try(var stream=PssCompensationNativeReference.class.getResourceAsStream("/pss-comp-native/"+name+".b64")){
            if(stream==null)throw new IllegalStateException("Missing compensation reference: "+name);
            byte[] bytes=Base64.getMimeDecoder().decode(stream.readAllBytes());
            if(zipped)try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=input.readAllBytes();}
            String text=new String(bytes,StandardCharsets.UTF_8);
            if(!WtgibffraNativeReference.digest(text).equals(digest))throw new IllegalStateException("Changed compensation reference: "+name);
            return text;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public record Profile(String name,String model,double step,int samples,double mva,String csvSha256,String inputSha256,String rawSha256){
        public double[] replayBounds(){return switch(name){
            case "pss6c-mva50-positive-coarse" -> new double[]{6.633e-05,1.494e-06,0.00030819,8.7814e-07,9.5432e-07,2.8796e-07,8.916e-10,2.4712e-11,1e-12,0.026514,6.7896e-05};
            case "pss6c-mva50-positive-fine" -> new double[]{3.5528e-05,2.9623e-07,0.00015664,2.645e-07,4.4615e-07,7.4237e-08,2.407e-10,1.5034e-11,1e-12,0.01377,3.3883e-05};
            case "pss6c-mva200-negative-coarse" -> new double[]{8.9137e-06,2.9541e-07,3.1454e-05,9.9345e-08,4.4373e-07,2.3001e-08,9.4083e-11,4.1096e-12,1e-12,0.00072059,6.1651e-06};
            case "pss6c-mva200-negative-fine" -> new double[]{4.9147e-06,4.4069e-07,1.5963e-05,2.0989e-08,7.7394e-07,6.0943e-08,5.3485e-10,1.1763e-11,1e-12,0.00036062,3.0573e-06};
            case "pss7c-mva50-positive-coarse" -> new double[]{4.9889e-06,1.4112e-07,7.0038e-06,4.9275e-06,1.6052e-05,0.00016014,2.1604e-07,4.4522e-08,2.2219e-08,1.292e-08,7.9057e-08,2.8972e-08,6.8211e-09,4.0932e-09,2.5887e-05,1.4124e-05,1.9122e-06,1.0637e-07,0.026522,0.00016129};
            case "pss7c-mva50-positive-fine" -> new double[]{5.7872e-06,7.0678e-08,3.528e-06,2.7758e-06,7.9847e-06,7.9747e-05,9.3205e-08,4.5478e-08,1.5218e-08,2.7914e-09,6.3811e-09,3.5566e-09,2.1427e-09,1.3636e-09,6.4767e-06,3.3595e-06,5.3771e-07,1.8992e-08,0.013776,7.3778e-05};
            case "pss7c-mva200-negative-coarse" -> new double[]{3.658e-06,2.6295e-08,1.1963e-06,5.0346e-07,1.6382e-06,1.6348e-05,5.8754e-08,4.2958e-08,2.5132e-08,1.1923e-08,4.5562e-08,1.3279e-08,2.4466e-09,5.1225e-10,2.7753e-06,1.6079e-06,2.2726e-07,1.4416e-08,0.00072095,1.6662e-05};
            case "pss7c-mva200-negative-fine" -> new double[]{6.1947e-06,8.4552e-08,2.3048e-06,2.5754e-07,8.1524e-07,8.1436e-06,1.8317e-07,1.1799e-07,6.1072e-08,2.6278e-08,9.6028e-08,3.0109e-08,8.1348e-09,1.8758e-09,7.0714e-07,4.1706e-07,6.1633e-08,4.2955e-09,0.00036038,7.6009e-06};
            default -> throw new IllegalArgumentException("Unknown compensation profile: "+name);
        };}
        public String body(){return resource(name+".csv",true,csvSha256);}
        public String input(){return resource(name+".dyr",false,inputSha256);}
        public String network(){return resource(name+".raw",false,rawSha256);}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("pss6c-mva50-positive-coarse","PSS6C",0.0005,605,50.0,"5ccc1ec642e4afc69c406a4090f4328ead4745a773e796a21fd9a4352ca74720","b74f5a961500a34ccb80a82c1fa7d7b5bfd8b29b79b1b2b3296522771825df40","ff6ab903c3207cfc265344f901e04b3eff1506d1855b7d456653a9b936930832"),
        new Profile("pss6c-mva50-positive-fine","PSS6C",0.00025,1205,50.0,"2009be2706f3ad123f92df970da9c6a6562f5112599e37f9f800909085462944","b74f5a961500a34ccb80a82c1fa7d7b5bfd8b29b79b1b2b3296522771825df40","ff6ab903c3207cfc265344f901e04b3eff1506d1855b7d456653a9b936930832"),
        new Profile("pss6c-mva200-negative-coarse","PSS6C",0.0005,605,200.0,"f692e62d4e4d536c816aff42116080ffe11001c4e7d0ca8ffc7eb3b9410acf4c","c33c34b77e17cb60fa047a91b3379c9afbe7da13f7f53b9d47c80c9ee75a4fbe","9aeb6770a50540561c96eb712000b86c73715502030f700e83d60eba9177873c"),
        new Profile("pss6c-mva200-negative-fine","PSS6C",0.00025,1205,200.0,"47b960a53ef79139fded952eff54855eefaa938eca8f240a8c48de685e551339","c33c34b77e17cb60fa047a91b3379c9afbe7da13f7f53b9d47c80c9ee75a4fbe","9aeb6770a50540561c96eb712000b86c73715502030f700e83d60eba9177873c"),
        new Profile("pss7c-mva50-positive-coarse","PSS7C",0.0005,605,50.0,"cd71ef65cd1a2d638a547744328f8ce3fa9b50b0f01b016d1f915504bd37f85c","bc410a9832dcccb31f4b978f3d3fe6169f84471ea77238fab5e05b8407db1115","ff6ab903c3207cfc265344f901e04b3eff1506d1855b7d456653a9b936930832"),
        new Profile("pss7c-mva50-positive-fine","PSS7C",0.00025,1205,50.0,"e91eceb0279004c699565241ba502730c6640d452c651553b6098042b98d3ae5","bc410a9832dcccb31f4b978f3d3fe6169f84471ea77238fab5e05b8407db1115","ff6ab903c3207cfc265344f901e04b3eff1506d1855b7d456653a9b936930832"),
        new Profile("pss7c-mva200-negative-coarse","PSS7C",0.0005,605,200.0,"f60144120dbf397efd4caa1aa2c71fef85952a9a9653be7880c0b2280d7a5bdc","81a53c85e09a2ef0003cd636a00706573f58e7fdcadb6c9ed3fe6a5ef1da02c6","9aeb6770a50540561c96eb712000b86c73715502030f700e83d60eba9177873c"),
        new Profile("pss7c-mva200-negative-fine","PSS7C",0.00025,1205,200.0,"541b992d66da9fc09d38348478398f1aa92710481c1490ba870afd1667df9fda","81a53c85e09a2ef0003cd636a00706573f58e7fdcadb6c9ed3fe6a5ef1da02c6","9aeb6770a50540561c96eb712000b86c73715502030f700e83d60eba9177873c")
    );}
}
