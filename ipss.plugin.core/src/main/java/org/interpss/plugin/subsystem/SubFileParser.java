package org.interpss.plugin.subsystem;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for PSS/E Subsystem Definition (.sub) files.
 *
 * Supported constructs:
 *   SYSTEM / SUBSYSTEM 'label'
 *   JOIN ['label'] ... END
 *   AREA n / AREAS n m  (ranges expanded into individual area numbers)
 *   ZONE n / ZONES n m
 *   OWNER n / OWNERS n m
 *   BUS n  / BUSES n m
 *   KV r   / KVRANGE r1 r2
 *   SKIP BUS n
 *   END  (ends JOIN or SYSTEM block)
 *
 * Comments begin with '/' and are ignored.
 */
public class SubFileParser {

    public SubsystemContainer parse(Path filePath) throws IOException {
        List<Subsystem> subsystems = new ArrayList<>();

        Subsystem currentSub  = null;
        JoinGroup currentJoin = null;
        boolean inJoin        = false;
        boolean inSubsystem   = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Strip inline comments (anything after '/')
                int commentIdx = line.indexOf('/');
                if (commentIdx >= 0) line = line.substring(0, commentIdx);
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] tokens = line.split("\\s+");
                String keyword  = tokens[0].toUpperCase();

                // ---- SYSTEM / SUBSYSTEM opener ----
                if (keyword.equals("SYSTEM") || keyword.equals("SUBSYSTEM")) {
                    currentSub  = new Subsystem(extractLabel(line), keyword);
                    inSubsystem = true;
                    inJoin      = false;
                    continue;
                }

                // ---- END : closes JOIN or SUBSYSTEM ----
                if (keyword.equals("END")) {
                    if (inJoin) {
                        currentSub.addJoinGroup(currentJoin);
                        currentJoin = null;
                        inJoin      = false;
                    } else if (inSubsystem) {
                        subsystems.add(currentSub);
                        currentSub  = null;
                        inSubsystem = false;
                    }
                    continue;
                }

                // ---- JOIN opener ----
                if (keyword.equals("JOIN")) {
                    String joinLabel = tokens.length > 1
                            ? extractLabel(line.substring("JOIN".length()).trim()) : null;
                    currentJoin = new JoinGroup(joinLabel);
                    inJoin      = true;
                    continue;
                }

                // ---- SKIP BUS n ----
                if (keyword.equals("SKIP") && tokens.length >= 3
                        && tokens[1].equalsIgnoreCase("BUS")) {
                    if (currentSub != null)
                        currentSub.addSkipBus(Integer.parseInt(tokens[2]));
                    continue;
                }

                // ---- Selection criteria ----
                SelectionGroup target = null;
                if (inJoin && currentJoin != null)          target = currentJoin.getSelection();
                else if (inSubsystem && currentSub != null) target = currentSub.getDirectSelection();

                if (target != null) applyKeyword(keyword, tokens, target);
            }
        }
        return new SubsystemContainer(filePath.toAbsolutePath().toString(), subsystems);
    }

    private void applyKeyword(String kw, String[] tokens, SelectionGroup g) {
        try {
            switch (kw) {
                case "AREA"    -> g.addArea(parseInt(tokens, 1));
                case "AREAS"   -> g.addAreaRange(parseInt(tokens, 1), parseInt(tokens, 2));
                case "ZONE"    -> g.addZone(parseInt(tokens, 1));
                case "ZONES"   -> g.addZoneRange(parseInt(tokens, 1), parseInt(tokens, 2));
                case "OWNER"   -> g.addOwner(parseInt(tokens, 1));
                case "OWNERS"  -> g.addOwnerRange(parseInt(tokens, 1), parseInt(tokens, 2));
                case "BUS"     -> g.addBus(parseInt(tokens, 1));
                case "BUSES"   -> g.addBusRange(parseInt(tokens, 1), parseInt(tokens, 2));
                case "KV"      -> g.addKv(parseDouble(tokens, 1));
                case "KVRANGE" -> g.setKvRange(parseDouble(tokens, 1), parseDouble(tokens, 2));
                default        -> { /* ignore unknown keywords */ }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) { }
    }

    /**
     * Extracts the quoted or unquoted label after the leading keyword.
     *   SYSTEM 'SWPA'   -> "SWPA"
     *   JOIN 'GROUP 1'  -> "GROUP 1"
     */
    private String extractLabel(String line) {
        int firstSpace = line.indexOf(' ');
        String labelPart = firstSpace >= 0 ? line.substring(firstSpace).trim() : "";
        if (labelPart.startsWith("'") || labelPart.startsWith("\"")) {
            int end = labelPart.lastIndexOf(labelPart.charAt(0));
            return end > 0 ? labelPart.substring(1, end) : labelPart.substring(1);
        }
        return labelPart.isEmpty() ? null : labelPart;
    }

    private int    parseInt(String[] t, int i)    { return Integer.parseInt(t[i]); }
    private double parseDouble(String[] t, int i) { return Double.parseDouble(t[i]); }
}
