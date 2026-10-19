package org.interpss.core.dstab.reference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Exact numerical comparison of the runner's SINGLE PowerWorld CSV exports, not a parity tolerance. */
public final class PowerWorldCsvRepeatability {
    private PowerWorldCsvRepeatability() {}

    public record Summary(int samples, int channels, int repeatedTimeSamples) {}

    public record ChannelDifference(int column,int changedSamples,double maximumAbsoluteError,double timeOfMaximum) {}
    public record Differences(Summary summary,List<ChannelDifference> channels) {
        public Differences { channels=List.copyOf(channels); }
    }

    /** Diagnostic only: no tolerance, sample removal or acceptance decision. */
    public static Differences differences(Path expected,Path actual)throws IOException {
        Table left=read(expected),right=read(actual);
        require(left.metadata.equals(right.metadata),"Channel metadata or contingency differs");
        require(left.rows.size()==right.rows.size(),"Sample count differs (event-side rows must be retained)");
        int[] changed=new int[left.channels];double[] maximum=new double[left.channels],at=new double[left.channels];int repeated=0;
        for(int row=0;row<left.rows.size();row++) {
            double[] a=left.rows.get(row),b=right.rows.get(row);require(a[0]==b[0],"Diagnostic timestamps differ; interpolation is not permitted");
            if(row>0&&a[0]==left.rows.get(row-1)[0])repeated++;
            for(int channel=0;channel<left.channels;channel++) {
                double error=Math.abs(a[channel+1]-b[channel+1]);if(error!=0)changed[channel]++;
                if(error>maximum[channel]){maximum[channel]=error;at[channel]=a[0];}
            }
        }
        List<ChannelDifference> channels=new ArrayList<>();for(int channel=0;channel<left.channels;channel++)channels.add(new ChannelDifference(channel+1,changed[channel],maximum[channel],at[channel]));
        return new Differences(new Summary(left.rows.size(),left.channels,repeated),channels);
    }

    public static Summary compare(Path expected, Path actual) throws IOException {
        Table left = read(expected);
        Table right = read(actual);
        require(left.metadata.equals(right.metadata), "Channel metadata or contingency differs");
        require(left.rows.size() == right.rows.size(), "Sample count differs (event-side rows must be retained)");
        int repeated = 0;
        for (int row = 0; row < left.rows.size(); row++) {
            double[] a = left.rows.get(row);
            double[] b = right.rows.get(row);
            if (row > 0 && a[0] == left.rows.get(row - 1)[0]) repeated++;
            for (int column = 0; column < a.length; column++) {
                require(a[column] == b[column], "Numerical difference at sample " + row
                        + ", time=" + a[0] + ", column=" + column
                        + ": expected=" + a[column] + ", actual=" + b[column]);
            }
        }
        return new Summary(left.rows.size(), left.channels, repeated);
    }

    private record Table(List<String> metadata, int channels, List<double[]> rows) {}

    private static Table read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        require(lines.size() >= 6 && lines.get(0).equals("ObjectFields")
                && lines.get(1).startsWith("ObjectType,"), "Missing PowerWorld field header: " + path);
        int fieldEnd = lines.indexOf("END");
        require(fieldEnd > 2 && fieldEnd + 2 < lines.size(), "Missing channel definitions: " + path);
        require(lines.get(fieldEnd + 1).startsWith("Results,"), "Expected SINGLE results: " + path);
        require(lines.get(lines.size() - 1).equals("END"), "Incomplete results export: " + path);
        int channels = fieldEnd - 2;
        List<double[]> rows = new ArrayList<>();
        for (int line = fieldEnd + 2; line < lines.size() - 1; line++) {
            String[] fields = lines.get(line).split(",", -1);
            // A newer PowerWorld serialization omits the old empty trailing field.
            // Remove only that one known empty field, never a channel or blank sample.
            if (fields.length == channels + 2 && fields[fields.length - 1].isEmpty()) {
                fields = Arrays.copyOf(fields, fields.length - 1);
            }
            require(fields.length == channels + 1, "Wrong result width at line " + (line + 1));
            double[] values = new double[fields.length];
            for (int column = 0; column < fields.length; column++) {
                try {
                    values[column] = Double.parseDouble(fields[column]);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid number at line " + (line + 1)
                            + ", column=" + column, ex);
                }
                require(Double.isFinite(values[column]), "Nonfinite value at line " + (line + 1));
            }
            require(rows.isEmpty() || values[0] >= rows.get(rows.size() - 1)[0],
                    "Time decreases at line " + (line + 1));
            rows.add(values);
        }
        require(!rows.isEmpty(), "Empty result export: " + path);
        return new Table(List.copyOf(lines.subList(0, fieldEnd + 2)), channels, rows);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public static void main(String[] args) throws IOException {
        if(args.length==3&&args[0].equals("--differences")) {
            var result=differences(Path.of(args[1]),Path.of(args[2]));
            System.out.println("column,changed_samples,max_abs_error,time_s");
            for(var channel:result.channels())System.out.println(channel.column()+","+channel.changedSamples()+","+channel.maximumAbsoluteError()+","+channel.timeOfMaximum());
            return;
        }
        require(args.length == 2, "Usage: PowerWorldCsvRepeatability expected.csv actual.csv");
        System.out.println(compare(Path.of(args[0]), Path.of(args[1])));
    }
}
