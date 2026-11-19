package org.interpss.core.opf.ecp;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.google.gson.*;
import com.interpss.opf.ecp.EcpOpfConfig;
import org.interpss.IpssCorePlugin;

/** Importer-to-core comparisons against frozen native MATPOWER 8.1 MIPS results.
 * Normal test execution needs no MATLAB installation or license. */
public class EcpOpfMatpowerComparisonTest {
    @TempDir Path temporary;
    @BeforeAll static void initialize(){IpssCorePlugin.init();}
    static JsonObject reference() throws Exception {
        try(var stream=EcpOpfMatpowerComparisonTest.class.getResourceAsStream("/opf/ecp/matpower81-reference.json")) {
            assertNotNull(stream);return JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    static Stream<Arguments> cases() throws Exception {
        return reference().getAsJsonObject("case_inputs").keySet().stream().flatMap(name->Stream.of("I","S")
                .flatMap(mode->Arrays.stream(EcpOpfConfig.Strategy.values()).map(strategy->Arguments.of(name,mode,strategy))));
    }
    @ParameterizedTest @MethodSource("cases")
    void nativeReferenceMatchesCostAndEveryOriginalPhysicalGate(String name,String mode,EcpOpfConfig.Strategy strategy) throws Exception {
        var helper=new EcpOpfPypowerComparisonTest();helper.temporary=temporary;
        helper.verify(name,mode,strategy,reference());
    }
}
