package org.interpss.plugin.contingency.aux_fmt;

import java.io.File;
import java.util.Locale;

import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;

import com.interpss.core.aclf.AclfNetwork;

public class AuxToContingencyJsonMain {
    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        AuxConversionOptions options = AuxConversionOptions.defaultOptions()
                .withBusIdMode(arguments.busIdMode)
                .withUnsupportedElementPolicy(arguments.unsupportedPolicy)
                .withDefaultCircuitId(arguments.defaultCircuitId);

        AclfNetwork network = loadNetwork(arguments.networkFile, arguments.psseVersion);
        AuxConversionReport report =
                new AuxContingencyConverter(network).convert(arguments.inputFile, arguments.outputFile, options);
        System.out.println("AUX contingencies: " + report.getContingencyCount());
        System.out.println("AUX CTG elements: " + report.getCtgElementCount());
        System.out.println("InterPSS branch records: " + report.getEmittedBranchRecordCount());
        System.out.println("Unsupported elements: " + report.getUnsupportedElementCount());
        System.out.println("Skipped elements: " + report.getSkippedElementCount());
        for (String warning : report.getWarnings()) {
            System.out.println("WARN: " + warning);
        }
    }

    private static AclfNetwork loadNetwork(File networkFile, PsseVersion psseVersion) throws Exception {
        PsseVersion version = psseVersion != null
                ? psseVersion
                : IpssAdapter.parsePsseVersion(networkFile.toString());
        return IpssAdapter.importAclfNet(networkFile.toString())
                .setFormat(FileFormat.PSSE)
                .setPsseVersion(version)
                .load()
                .getImportedObj();
    }

    private static final class Arguments {
        private File inputFile;
        private File outputFile;
        private File networkFile;
        private PsseVersion psseVersion;
        private AuxConversionOptions.BusIdMode busIdMode = AuxConversionOptions.BusIdMode.PREFIX_BUS;
        private AuxConversionOptions.UnsupportedElementPolicy unsupportedPolicy =
                AuxConversionOptions.UnsupportedElementPolicy.WARN;
        private String defaultCircuitId = "1";

        private static Arguments parse(String[] args) {
            Arguments parsed = new Arguments();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--input":
                        parsed.inputFile = new File(requiredValue(args, ++i, arg));
                        break;
                    case "--output":
                        parsed.outputFile = new File(requiredValue(args, ++i, arg));
                        break;
                    case "--network":
                        parsed.networkFile = new File(requiredValue(args, ++i, arg));
                        break;
                    case "--psse-version":
                        parsed.psseVersion = parsePsseVersion(requiredValue(args, ++i, arg));
                        break;
                    case "--bus-id-mode":
                        parsed.busIdMode = parseBusIdMode(requiredValue(args, ++i, arg));
                        break;
                    case "--unsupported":
                        parsed.unsupportedPolicy = parseUnsupportedPolicy(requiredValue(args, ++i, arg));
                        break;
                    case "--default-circuit":
                        parsed.defaultCircuitId = requiredValue(args, ++i, arg);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown argument: " + arg + "\n" + usage());
                }
            }
            if (parsed.inputFile == null || parsed.outputFile == null || parsed.networkFile == null) {
                throw new IllegalArgumentException(usage());
            }
            return parsed;
        }

        private static String requiredValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return args[index];
        }

        private static AuxConversionOptions.BusIdMode parseBusIdMode(String value) {
            String normalized = value.toLowerCase(Locale.ROOT).replace('_', '-');
            if ("prefix-bus".equals(normalized)) {
                return AuxConversionOptions.BusIdMode.PREFIX_BUS;
            }
            if ("preserve".equals(normalized)) {
                return AuxConversionOptions.BusIdMode.PRESERVE;
            }
            throw new IllegalArgumentException("Unsupported bus id mode: " + value);
        }

        private static AuxConversionOptions.UnsupportedElementPolicy parseUnsupportedPolicy(String value) {
            String normalized = value.toLowerCase(Locale.ROOT);
            if ("warn".equals(normalized)) {
                return AuxConversionOptions.UnsupportedElementPolicy.WARN;
            }
            if ("fail".equals(normalized)) {
                return AuxConversionOptions.UnsupportedElementPolicy.FAIL;
            }
            throw new IllegalArgumentException("Unsupported unsupported-element policy: " + value);
        }

        private static PsseVersion parsePsseVersion(String value) {
            String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_');
            if ("AUTO".equals(normalized)) {
                return null;
            }
            if (!normalized.startsWith("PSSE_")) {
                normalized = "PSSE_" + normalized;
            }
            return PsseVersion.valueOf(normalized);
        }

        private static String usage() {
            return "Usage: --input <aux-file> --output <json-file> --network <psse-raw-file> "
                    + "[--psse-version auto|29|30|31|32|33|34|35|36] "
                    + "[--bus-id-mode prefix-bus|preserve] [--unsupported warn|fail] "
                    + "[--default-circuit <id>]";
        }
    }
}
