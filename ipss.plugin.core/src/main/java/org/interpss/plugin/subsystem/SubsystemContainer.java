package org.interpss.plugin.subsystem;

import java.util.List;

/**
 * Top-level container representing a parsed PSS/E Subsystem Definition (.sub) file.
 */
public record SubsystemContainer(
        /** Original source file path. */
        String sourceFile,

        /** All subsystem definitions found in the file, in order. */
        List<Subsystem> subsystems
) {}
