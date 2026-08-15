package org.interpss.plugin.contingency.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.BranchRatingProvider;

/**
 * Imports custom monitored-branch contingency ratings from JSON or CSV files.
 */
public final class BranchRatingFileUtil {
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(BranchRatingFileUtil.class);

    private BranchRatingFileUtil() {
    }

    /**
     * Imports custom MVA ratings and returns a provider that falls back to
     * Rating B for branches missing from the file.
     */
    public static BranchRatingProvider importBranchRatingProvider(File file) throws IOException {
        return BranchRatingProvider.custom(importBranchRatings(file));
    }

    public static BranchRatingProvider importBranchRatingProvider(Path path) throws IOException {
        return importBranchRatingProvider(path.toFile());
    }

    /**
     * Imports custom MVA ratings and builds a provider keyed by either internal
     * branch id or branch extUID.
     */
    public static BranchRatingProvider importBranchRatingProvider(File file, boolean useExtUID) throws IOException {
        return BranchRatingProvider.custom(importBranchRatings(file, useExtUID), useExtUID);
    }

    public static BranchRatingProvider importBranchRatingProvider(Path path, boolean useExtUID) throws IOException {
        return importBranchRatingProvider(path.toFile(), useExtUID);
    }

    /**
     * Imports custom ratings and resolves each row by either InterPSS branch id
     * or branch extUID.
     */
    public static BranchRatingProvider importBranchRatingProvider(AclfNetwork net, File file) throws IOException {
        return BranchRatingProvider.custom(importBranchRatings(net, file));
    }

    public static BranchRatingProvider importBranchRatingProvider(AclfNetwork net, Path path) throws IOException {
        return importBranchRatingProvider(net, path.toFile());
    }

    /**
     * Imports custom MVA ratings keyed by InterPSS branch id.
     */
    public static Map<String, Double> importBranchRatings(File file) throws IOException {
        String name = file.getName().toLowerCase(Locale.ROOT);
        Map<String, Double> ratings;
        if (name.endsWith(".json")) {
            ratings = importJson(file);
        } else if (name.endsWith(".csv")) {
            ratings = importCsv(file.toPath());
        } else {
            throw new IOException("Unsupported branch rating file extension: " + file.getName()
                    + ". Expected .json or .csv");
        }
        if (ratings.isEmpty()) {
            throw new IOException("No branch ratings found in file: " + file.getName());
        }
        log.info("Imported {} custom branch ratings from file: {}", ratings.size(), file.getName());
        return ratings;
    }

    public static Map<String, Double> importBranchRatings(Path path) throws IOException {
        return importBranchRatings(path.toFile());
    }

    /**
     * Imports custom MVA ratings keyed by either InterPSS branch id or extUID.
     * When {@code useExtUID=true}, explicit {@code extUID} fields are preferred;
     * otherwise the primary id column/key is treated as the extUID.
     */
    public static Map<String, Double> importBranchRatings(File file, boolean useExtUID) throws IOException {
        Map<String, Double> ratings = new LinkedHashMap<>();
        for (BranchRatingInput input : importBranchRatingInputs(file)) {
            String key = useExtUID
                    ? input.extUID() != null ? input.extUID() : input.branchId()
                    : input.branchId();
            if (key == null || key.isBlank()) {
                throw new IOException("Branch rating file " + file.getName()
                        + " is missing " + (useExtUID ? "extUID" : "branch_id") + " for one or more records");
            }
            addRating(ratings, key, input.ratingMva());
        }
        return ratings;
    }

    public static Map<String, Double> importBranchRatings(Path path, boolean useExtUID) throws IOException {
        return importBranchRatings(path.toFile(), useExtUID);
    }

    /**
     * Imports custom ratings and normalizes all records to InterPSS branch ids.
     * Explicit {@code branch_id}/{@code branchId} fields are used directly.
     * Explicit {@code extUID}/{@code ext_uid} fields are resolved through the
     * network. Ambiguous key-only formats try branch id first, then extUID.
     */
    public static Map<String, Double> importBranchRatings(AclfNetwork net, File file) throws IOException {
        if (net == null) {
            throw new IOException("AclfNetwork cannot be null when resolving branch extUID ratings");
        }
        List<BranchRatingInput> inputs = importBranchRatingInputs(file);
        Map<String, AclfBranch> extUidIndex = buildExtUidIndex(net);
        Map<String, Double> ratings = new LinkedHashMap<>();
        for (BranchRatingInput input : inputs) {
            AclfBranch branch = resolveBranch(net, extUidIndex, input);
            addRating(ratings, branch.getId(), input.ratingMva());
        }
        log.info("Resolved {} custom branch ratings from file {} using branch id/extUID",
                ratings.size(), file.getName());
        return ratings;
    }

    public static Map<String, Double> importBranchRatings(AclfNetwork net, Path path) throws IOException {
        return importBranchRatings(net, path.toFile());
    }

    private static List<BranchRatingInput> importBranchRatingInputs(File file) throws IOException {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".json")) {
            return importJsonInputs(file);
        }
        if (name.endsWith(".csv")) {
            return importCsvInputs(file.toPath());
        }
        throw new IOException("Unsupported branch rating file extension: " + file.getName()
                + ". Expected .json or .csv");
    }

    private static Map<String, Double> importJson(File file) throws IOException {
        Map<String, Double> ratings = new LinkedHashMap<>();
        for (BranchRatingInput input : importJsonInputs(file)) {
            if (input.branchId() == null || input.branchId().isBlank()) {
                throw new IOException("Branch rating file " + file.getName()
                        + " contains extUID-only records. Use importBranchRatings(AclfNetwork, File)");
            }
            addRating(ratings, input.branchId(), input.ratingMva());
        }
        return ratings;
    }

    private static List<BranchRatingInput> importJsonInputs(File file) throws IOException {
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            List<BranchRatingInput> ratings = new ArrayList<>();
            if (root.isJsonArray()) {
                readJsonRecords(root, ratings);
            } else if (root.isJsonObject()) {
                JsonObject object = root.getAsJsonObject();
                JsonElement records = firstPresent(object,
                        "branch_ratings", "branchRatings", "ratings", "custom_branch_ratings");
                if (records != null) {
                    readJsonRecords(records, ratings);
                } else {
                    readJsonMap(object, ratings);
                }
            } else {
                throw new IOException("Invalid branch rating JSON format: " + file.getName());
            }
            return ratings;
        } catch (RuntimeException e) {
            throw new IOException("Invalid branch rating JSON format: " + file.getName(), e);
        }
    }

    private static void readJsonRecords(JsonElement records, List<BranchRatingInput> ratings) throws IOException {
        if (!records.isJsonArray()) {
            throw new IOException("Branch rating records must be a JSON array");
        }
        for (JsonElement record : records.getAsJsonArray()) {
            if (!record.isJsonObject()) {
                throw new IOException("Branch rating record must be a JSON object");
            }
            JsonObject object = record.getAsJsonObject();
            String branchId = stringField(object, "branch_id", "branchId", "id");
            String extUID = stringField(object, "extUID", "ext_uid", "external_uid", "externalUid");
            Double rating = numericField(object, "rating_mva", "ratingMva", "rating", "mva");
            ratings.add(validatedInput(branchId, extUID, rating));
        }
    }

    private static void readJsonMap(JsonObject object, List<BranchRatingInput> ratings) throws IOException {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                ratings.add(validatedInput(entry.getKey(), null, value.getAsDouble()));
            } else if (value.isJsonObject()) {
                JsonObject valueObject = value.getAsJsonObject();
                String branchId = stringField(valueObject, "branch_id", "branchId", "id");
                String extUID = stringField(valueObject, "extUID", "ext_uid", "external_uid", "externalUid");
                Double rating = numericField(valueObject, "rating_mva", "ratingMva", "rating", "mva");
                ratings.add(validatedInput(
                        branchId == null && extUID == null ? entry.getKey() : branchId,
                        extUID,
                        rating));
            } else {
                throw new IOException("Invalid branch rating value for branch " + entry.getKey());
            }
        }
    }

    private static Map<String, Double> importCsv(Path path) throws IOException {
        Map<String, Double> ratings = new LinkedHashMap<>();
        for (BranchRatingInput input : importCsvInputs(path)) {
            if (input.branchId() == null || input.branchId().isBlank()) {
                throw new IOException("Branch rating file " + path.getFileName()
                        + " contains extUID-only records. Use importBranchRatings(AclfNetwork, File)");
            }
            addRating(ratings, input.branchId(), input.ratingMva());
        }
        return ratings;
    }

    private static List<BranchRatingInput> importCsvInputs(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<BranchRatingInput> ratings = new ArrayList<>();
        int branchIdColumn = 0;
        int extUidColumn = -1;
        int ratingColumn = 1;
        boolean headerRead = false;
        boolean headerPresent = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            List<String> columns = splitCsv(line);
            if (!headerRead) {
                headerRead = true;
                int detectedBranchIdColumn = findColumn(columns, "branchid", "branch");
                int detectedExtUidColumn = findColumn(columns, "extuid", "externaluid");
                int detectedRatingColumn = findColumn(columns, "ratingmva", "ratingmw", "rating", "mva");
                headerPresent = detectedBranchIdColumn >= 0 || detectedExtUidColumn >= 0 || detectedRatingColumn >= 0;
                if (headerPresent) {
                    if ((detectedBranchIdColumn < 0 && detectedExtUidColumn < 0) || detectedRatingColumn < 0) {
                        throw new IOException("CSV header must include branch_id or extUID, plus rating_mva columns");
                    }
                    branchIdColumn = detectedBranchIdColumn;
                    extUidColumn = detectedExtUidColumn;
                    ratingColumn = detectedRatingColumn;
                    continue;
                }
            }
            int maxColumn = Math.max(Math.max(branchIdColumn, extUidColumn), ratingColumn);
            if (columns.size() <= maxColumn) {
                throw new IOException("Invalid branch rating CSV row at line " + (i + 1));
            }
            String branchId = branchIdColumn >= 0 ? columns.get(branchIdColumn).trim() : null;
            String extUID = extUidColumn >= 0 ? columns.get(extUidColumn).trim() : null;
            Double rating = parseRating(columns.get(ratingColumn).trim(), "CSV line " + (i + 1));
            ratings.add(validatedInput(branchId, extUID, rating));
        }
        return ratings;
    }

    private static JsonElement firstPresent(JsonObject object, String... names) {
        for (String name : names) {
            if (object.has(name)) {
                return object.get(name);
            }
        }
        return null;
    }

    private static String stringField(JsonObject object, String... names) {
        for (String name : names) {
            JsonElement value = object.get(name);
            if (value != null && !value.isJsonNull()) {
                return value.getAsString();
            }
        }
        return null;
    }

    private static Double numericField(JsonObject object, String... names) {
        for (String name : names) {
            JsonElement value = object.get(name);
            if (value != null && !value.isJsonNull()) {
                return value.getAsDouble();
            }
        }
        return null;
    }

    private static int findColumn(List<String> columns, String... normalizedNames) {
        for (int i = 0; i < columns.size(); i++) {
            String name = normalizeHeader(columns.get(i));
            for (String normalizedName : normalizedNames) {
                if (name.equals(normalizedName)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String normalizeHeader(String header) {
        return header.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    private static Double parseRating(String text, String source) throws IOException {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid branch rating at " + source + ": " + text, e);
        }
    }

    private static void addRating(Map<String, Double> ratings, String branchId, Double rating) throws IOException {
        if (branchId == null || branchId.isBlank()) {
            throw new IOException("Branch rating id cannot be null or blank");
        }
        if (rating == null || !Double.isFinite(rating) || rating < 0.0) {
            throw new IOException("Custom rating for branch " + branchId
                    + " must be a finite non-negative MVA value");
        }
        Double previous = ratings.put(branchId, rating);
        if (previous != null) {
            log.warn("Duplicate custom branch rating for branch {}, overriding previous value", branchId);
        }
    }

    private static BranchRatingInput validatedInput(String branchId, String extUID, Double rating) throws IOException {
        branchId = blankToNull(branchId);
        extUID = blankToNull(extUID);
        if (branchId == null && extUID == null) {
            throw new IOException("Branch rating record must define branch_id or extUID");
        }
        if (rating == null || !Double.isFinite(rating) || rating < 0.0) {
            String id = branchId != null ? branchId : extUID;
            throw new IOException("Custom rating for branch " + id
                    + " must be a finite non-negative MVA value");
        }
        return new BranchRatingInput(branchId, extUID, rating);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, AclfBranch> buildExtUidIndex(AclfNetwork net) throws IOException {
        Map<String, AclfBranch> index = new LinkedHashMap<>();
        for (AclfBranch branch : net.getBranchList()) {
            String extUID = blankToNull(branch.getExtUID());
            if (extUID == null) {
                continue;
            }
            AclfBranch previous = index.put(extUID, branch);
            if (previous != null && previous != branch) {
                throw new IOException("Duplicate branch extUID " + extUID + " for branches "
                        + previous.getId() + " and " + branch.getId());
            }
        }
        return index;
    }

    private static AclfBranch resolveBranch(
            AclfNetwork net,
            Map<String, AclfBranch> extUidIndex,
            BranchRatingInput input) throws IOException {
        if (input.branchId() != null) {
            AclfBranch branch = net.getBranch(input.branchId());
            if (branch != null) {
                return branch;
            }
            branch = extUidIndex.get(input.branchId());
            if (branch != null && input.extUID() == null) {
                return branch;
            }
            if (input.extUID() == null) {
                throw new IOException("Custom branch rating references unknown branch id/extUID: "
                        + input.branchId());
            }
        }
        AclfBranch branch = extUidIndex.get(input.extUID());
        if (branch == null) {
            throw new IOException("Custom branch rating references unknown branch extUID: " + input.extUID());
        }
        return branch;
    }

    private static List<String> splitCsv(String line) throws IOException {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (quoted) {
            throw new IOException("Unclosed quoted CSV field: " + line);
        }
        columns.add(current.toString());
        return columns;
    }

    private record BranchRatingInput(String branchId, String extUID, double ratingMva) {
    }
}
