package org.interpss.core.dstab;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import com.interpss.dstab.controller.oel.basoel2.Basoel2Kernel.TimerSemantics;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** End-to-end documented-policy record loading; independent trajectory acceptance is separate. */
public class Basoel2ImportedSmibTest {
    private static Path output;
    private static synchronized Path directory()throws Exception {
        if(output==null){
            Path classes=Path.of(Basoel2ImportedSmibTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            output=Files.createTempDirectory(classes.getParent(),"basoel2-imported-coupled-");
        }
        return output;
    }
    static Stream<Arguments> profiles(){
        return Stream.of("flat","flat-vhz","efd-fault","ifd-fault","vfe-fault","vhz-fault","disabled-fault","ifd-zero-filter")
            .flatMap(profile->Stream.of(.0005,.00025).map(step->Arguments.of(profile,step)));
    }
    @ParameterizedTest @MethodSource("profiles")
    void importedModelRunsFlatAndThreeCycleFaultProfiles(String profile,double step)throws Exception {
        Basoel2SmibTest.run(profile,step,TimerSemantics.POWERWORLD_DOCUMENTED,false,directory(),true);
    }
}
