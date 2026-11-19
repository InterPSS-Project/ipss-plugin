package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;

/** Frozen input identity, not acceptance of every model equation or profile. */
public class ExciterInputContractTest {
    private static final String CATALOG_SHA="6f4c78e5cf85cbdaf8378fead2d618470bb8fad3b2e9dff6c221a1c8cf5d31f6";
    private static byte[] resource(String name)throws Exception {
        try(var input=ExciterInputContractTest.class.getResourceAsStream(name)){return input==null?null:input.readAllBytes();}
    }
    private static byte[] canonical(byte[] body){return new String(body,StandardCharsets.UTF_8).replace("\r\n","\n").replace('\r','\n').getBytes(StandardCharsets.UTF_8);}
    private static String sha(byte[] body)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));}
    private static JsonArray contracts()throws Exception {
        byte[] body=resource("/exciter-input-contracts.json");assertNotNull(body);assertEquals(CATALOG_SHA,sha(canonical(body)));
        return JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("benchmarks");
    }

    @Test void thirtyAssetsAreContentAddressedAndDetectMutation()throws Exception {
        var location=ExciterInputContractTest.class.getResource("/exciter-input-assets");assertNotNull(location);int count=0;
        try(var files=Files.list(Path.of(location.toURI()))) {
            for(Path path:files.filter(p->p.getFileName().toString().endsWith(".b64")).toList()) {
                String expected=path.getFileName().toString().replace(".b64","");assertTrue(expected.matches("[0-9a-f]{64}"));
                byte[] body=Base64.getMimeDecoder().decode(Files.readAllBytes(path));assertEquals(expected,sha(canonical(body)),path.getFileName().toString());
                body[0]^=1;assertNotEquals(expected,sha(canonical(body)));count++;
            }
        }
        assertEquals(30,count);
    }

    @Test void elevenCompleteContractsDoNotHideSixUnavailableHistoricalSpecs()throws Exception {
        Set<String> complete=new TreeSet<>(),missing=new TreeSet<>();var contracts=contracts();assertEquals(17,contracts.size());
        for(var item:contracts) {
            var benchmark=item.getAsJsonObject();String name=benchmark.get("name").getAsString();boolean available=true;
            for(var entry:benchmark.getAsJsonObject("inputs").entrySet()) {
                var input=entry.getValue().getAsJsonObject();String path=input.get("path").getAsString(),expected=input.get("sha256").getAsString();
                assertFalse(path.contains(".."));assertFalse(path.contains(":"));assertTrue(path.startsWith("ipss.test.plugin.core/"));
                byte[] asset=resource("/exciter-input-assets/"+expected+".b64");byte[] body=asset==null?null:Base64.getMimeDecoder().decode(asset);
                if(body==null) {
                    Path fixture=Path.of(path.substring("ipss.test.plugin.core/".length()));
                    if(Files.isRegularFile(fixture)&&sha(canonical(Files.readAllBytes(fixture))).equals(expected))body=Files.readAllBytes(fixture);
                }
                if(body==null){assertEquals("spec",entry.getKey(),"Only the six historical specification gaps remain");available=false;}
                else assertEquals(expected,sha(canonical(body)),name+" "+entry.getKey());
            }
            assertTrue((available?complete:missing).add(name));
        }
        assertEquals(11,complete.size());assertEquals(Set.of("smib-genrou-ac7b","smib-genrou-esac4a","smib-genrou-esac6a","smib-genrou-esac8b","smib-genrou-exac4","smib-genrou-rexsys"),missing);
    }
}
