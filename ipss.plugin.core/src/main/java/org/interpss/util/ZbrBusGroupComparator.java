package org.interpss.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ZbrBusGroupComparator {

    /**
     * Parses a text file to extract bus groupings.
     * It looks for a specific header and then reads each line of comma-separated bus IDs.
     *
     * @param filePath The path to the text file.
     * @return A Set of Sets, where each inner Set contains the bus IDs of a single group.
     * @throws IOException If the file cannot be read.
     */
    public static Set<Set<String>> parseBusGroupsFromFile(String filePath) throws IOException {
        Set<Set<String>> fileGroups = new HashSet<>();
        boolean isDataSection = false;
        Set<String> lastGroup = null;

        List<String> lines = Files.readAllLines(Paths.get(filePath));

        for (String line : lines) {
            // Start processing after the header line
            if (line.trim().startsWith("ZERO IMPEDANCE LINE CONNECTED BUSES")) {
                isDataSection = true;
                continue;
            }

            // Skip lines until the data section is found or if the line is empty
            if (!isDataSection || line.trim().isEmpty()) {
                continue;
            }

            // Parse the line: split by comma, trim whitespace, and collect into a Set
            Set<String> currentGroup = Arrays.stream(line.split(","))
                                      .map(String::trim) // Remove leading/trailing spaces from each bus ID
                                      .filter(id -> !id.isEmpty()) // Ignore empty strings
                                      .collect(Collectors.toSet());
            
            if (!currentGroup.isEmpty()) {
                if (lastGroup != null && !java.util.Collections.disjoint(lastGroup, currentGroup)) {
                    // If the current group intersects with the last one, merge them
                    lastGroup.addAll(currentGroup);
                } else {
                    // Otherwise, this is a new group
                    lastGroup = currentGroup;
                    fileGroups.add(lastGroup);
                }
            }
        }
        return fileGroups;
    }

    public static boolean  compareZbrBusGroups (Map<String, List<String>> zbrGroupMap, Set<Set<String>> comparedZbrGroupSet){
       
        // --- Step 2: Convert the map's values to a Set of Sets for comparison ---
        // This removes the group names (keys) and makes the comparison order-independent.
        Set<Set<String>> mapGroups = zbrGroupMap.values().stream()
												.map(list -> list.stream()
																.map(id -> id.replace("Bus", ""))
																.collect(Collectors.toSet()))
                                                .collect(Collectors.toSet());

        // --- Step 4: Compare the two sets ---
        // System.out.println("Groups from Map: " + mapGroups);
        // System.out.println("Groups from File: " + fileGroups);

        if (mapGroups.equals(comparedZbrGroupSet)) {
            System.out.println("\nSUCCESS: The groups in the map and the file are identical.");
            return true;
        } else {
            System.out.println("\nFAILURE: The groups do not match.");

            // Optional: Find which groups are different
            Set<Set<String>> mapOnly = new HashSet<>(mapGroups);
            mapOnly.removeAll(comparedZbrGroupSet);
            System.out.println("Groups found only in the map: " + mapOnly);

            Set<Set<String>> fileOnly = new HashSet<>(comparedZbrGroupSet);
            fileOnly.removeAll(mapGroups);
            System.out.println("Groups found only in the file: " + fileOnly);

            return false;
        }
    }
}