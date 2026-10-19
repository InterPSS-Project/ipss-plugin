package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.interpss.core.dstab.reference.NativeFloatCsvCodec;
import org.junit.jupiter.api.Test;

public class NativeFloatCsvCodecTest {
    private static byte[] payload(float... values) {
        byte[] header="time_s,value\n".getBytes(StandardCharsets.UTF_8);
        var buffer=ByteBuffer.allocate(20+header.length+Float.BYTES*values.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{'F','3','2','C','S','V','1',0});buffer.putInt(values.length/2);buffer.putInt(2);buffer.putInt(header.length);
        buffer.put(header);for(float value:values)buffer.putFloat(value);return buffer.array();
    }
    @Test void exactDecimalRoundingPreservesGeneralFormatAndBothZeroSigns() {
        float[] values={0f,-0f,1f,.1f,.1221923828125f,1e-5f,1e-4f,Float.MIN_VALUE,Float.MAX_VALUE};
        String[] expected={"0","-0","1","0.10000000149","0.122192382812","9.99999974738e-06","9.99999974738e-05","1.40129846432e-45","3.40282346639e+38"};
        for(int j=0;j<values.length;j++)assertEquals(expected[j],NativeFloatCsvCodec.format(values[j]));
    }
    @Test void historyAndRepeatedEventRowsAreNotNormalizedOrDropped() {
        assertEquals("time_s,value\n-0.000500000023749,1\n0,1\n0,2\n0.000500000023749,3\n",
                NativeFloatCsvCodec.decode(payload(-.0005f,1,0,1,0,2,.0005f,3)));
    }
    @Test void malformedDimensionsTruncationAndTrailingDataAreRejected() {
        byte[] bytes=payload(0,1);
        assertThrows(IllegalArgumentException.class,()->NativeFloatCsvCodec.decode(Arrays.copyOf(bytes,bytes.length-1)));
        assertThrows(IllegalArgumentException.class,()->NativeFloatCsvCodec.decode(Arrays.copyOf(bytes,bytes.length+1)));
        bytes[8]=-1;bytes[9]=-1;bytes[10]=-1;bytes[11]=-1;
        assertThrows(IllegalArgumentException.class,()->NativeFloatCsvCodec.decode(bytes));
        assertThrows(IllegalArgumentException.class,()->NativeFloatCsvCodec.decode(new byte[20]));
    }
    @Test void nonfiniteChannelsAreNeverAccepted() {
        for(float value:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->NativeFloatCsvCodec.format(value));
            assertThrows(IllegalArgumentException.class,()->NativeFloatCsvCodec.decode(payload(0,value)));
        }
    }
}
