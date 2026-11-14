package org.interpss.core.dstab;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import com.interpss.dstab.controller.oel.basoel2.Basoel2Kernel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Exact-case impedance-load exports for independent PowerWorld policy comparison. */
public class Basoel2PowerWorldSmibTest {
    private static Path output;
    private static synchronized Path outputDirectory()throws Exception{
        if(output==null){
            Path classes=Path.of(Basoel2PowerWorldSmibTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            output=Files.createTempDirectory(classes.getParent(),"basoel2-powerworld-coupled-");
        }
        return output;
    }
    static Stream<Arguments> profiles(){
        return Stream.of("efd-fault","ifd-fault","vfe-fault","ifd-zero-filter","disabled-fault")
            .flatMap(profile->Stream.of(.0005,.00025).map(h->Arguments.of(profile,h)));
    }
    @ParameterizedTest @MethodSource("profiles")
    void exactIndependentCaseExportsEveryTimerAndEventSide(String profile,double h)throws Exception{
        Basoel2SmibTest.run(profile,h,Basoel2Kernel.TimerSemantics.POWERWORLD_DOCUMENTED,true,outputDirectory());
    }
}
