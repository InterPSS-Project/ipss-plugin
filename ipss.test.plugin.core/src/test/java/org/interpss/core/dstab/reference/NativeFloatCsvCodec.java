package org.interpss.core.dstab.reference;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Lossless storage for raw CSVs serialized from native binary32 channels at twelve significant digits. */
public final class NativeFloatCsvCodec {
    private static final byte[] MAGIC={'F','3','2','C','S','V','1',0};
    private static final MathContext PRECISION=new MathContext(12,RoundingMode.HALF_EVEN);
    private NativeFloatCsvCodec() {}

    public static String decode(byte[] payload) {
        if(payload.length<20 || !Arrays.equals(MAGIC,Arrays.copyOf(payload,8)))
            throw new IllegalArgumentException("Invalid native-float CSV payload");
        var buffer=ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);buffer.position(8);
        int rows=buffer.getInt(),columns=buffer.getInt(),headerSize=buffer.getInt();
        if(rows<=0 || columns<=0 || headerSize<=0 || headerSize>buffer.remaining())
            throw new IllegalArgumentException("Invalid native-float CSV dimensions");
        long expected=(long)rows*columns*Float.BYTES;
        if(expected!=buffer.remaining()-headerSize)
            throw new IllegalArgumentException("Truncated or trailing native-float CSV data");
        byte[] header=new byte[headerSize];buffer.get(header);
        String names=new String(header,StandardCharsets.UTF_8);
        if(!names.endsWith("\n") || names.indexOf('\n')!=names.length()-1 || names.contains("\r")
                || names.substring(0,names.length()-1).split(",",-1).length!=columns)
            throw new IllegalArgumentException("Invalid native-float CSV header");
        var text=new StringBuilder(names);
        for(int row=0;row<rows;row++) {
            for(int column=0;column<columns;column++) {
                if(column>0)text.append(',');text.append(format(buffer.getFloat()));
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** Exact binary value, ties-to-even decimal rounding and general-format exponent rules. */
    public static String format(float value) {
        if(!Float.isFinite(value))throw new IllegalArgumentException("Nonfinite native channel");
        if(value==0)return Float.floatToRawIntBits(value)<0?"-0":"0";
        var decimal=new BigDecimal((double)value).round(PRECISION).stripTrailingZeros();
        int exponent=decimal.precision()-decimal.scale()-1;
        if(exponent>=-4 && exponent<12)return decimal.toPlainString();
        String power=(exponent<0?"-":"+")+(Math.abs(exponent)<10?"0":"")+Math.abs(exponent);
        return decimal.movePointLeft(exponent).toPlainString()+"e"+power;
    }
}
