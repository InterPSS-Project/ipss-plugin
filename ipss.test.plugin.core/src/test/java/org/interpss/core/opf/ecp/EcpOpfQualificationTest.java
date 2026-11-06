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
import com.interpss.opf.ecp.*;
import org.interpss.IpssCorePlugin;

/** Controlled binding congestion, loading, individual-unit and supplied-seed
 * variants. The engineering checks are shared with the small-system comparison. */
public class EcpOpfQualificationTest {
    @TempDir Path temporary;
    @BeforeAll static void initialize() {IpssCorePlugin.init();}
    static JsonObject reference() throws Exception {
        try(var stream=EcpOpfQualificationTest.class.getResourceAsStream("/opf/ecp/pypower-qualification.json")) {
            assertNotNull(stream);return JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    static Stream<Arguments> cases() throws Exception {
        return reference().getAsJsonObject("case_inputs").keySet().stream()
                .filter(name->System.getProperty("ecp.reference.case", "").isEmpty() || name.equals(System.getProperty("ecp.reference.case"))).flatMap(name->Stream.of("I","S")
                .flatMap(mode->Arrays.stream(EcpOpfConfig.Strategy.values())
                        .filter(strategy->System.getProperty("ecp.reference.strategy", "").isEmpty() || strategy.name().equals(System.getProperty("ecp.reference.strategy")))
                        .map(strategy->Arguments.of(name,mode,strategy))));
    }
    @ParameterizedTest @MethodSource("cases")
    void controlledVariantsMatchReferenceAndEnforceTheActiveConstraints(String name,String mode,EcpOpfConfig.Strategy strategy) throws Exception {
        var report=reference();var helper=new EcpOpfPypowerComparisonTest();helper.temporary=temporary;
        var result=helper.verify(name,mode,strategy,report);
        var fixture=report.getAsJsonObject("case_inputs").getAsJsonObject(name);
        if(name.contains("rating")) {
            int row=fixture.getAsJsonObject("derivation").get("branch_row_zero_based").getAsInt();
            var input=fixture.getAsJsonObject("input");var branch=input.getAsJsonArray("branch").get(row).getAsJsonArray();
            String from="Bus"+branch.get(0).getAsInt(),to="Bus"+branch.get(1).getAsInt();
            var flow=result.branchFlows().get(from+"->"+to+"(1)");assertNotNull(flow);
            double magnitude=mode.equals("I")?Math.max(flow.fromCurrent().abs(),flow.toCurrent().abs()):Math.max(flow.fromPower().abs(),flow.toPower().abs());
            double rating=branch.get(5).getAsDouble()/input.get("baseMVA").getAsDouble();
            assertEquals(rating,magnitude,2e-6,"Tightened branch must actually bind");
        }
        if(name.contains("individual")) {
            assertEquals(4,result.generation().size(),"Offline unit excluded, colocated units retained");
            assertEquals(2,result.generation().keySet().stream().filter(key->key.busId().equals("Bus100")).count());
        }
    }
}
