package org.interpss.plugin.contingency.con_format.parser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.interpss.plugin.contingency.con_format.ConContainer;
import org.interpss.plugin.contingency.con_format.bean.ConBranchAction;
import org.interpss.plugin.contingency.con_format.bean.ConBranchEvent;
import org.interpss.plugin.contingency.con_format.bean.ConBusEvent;
import org.interpss.plugin.contingency.con_format.bean.ConBusModAction;
import org.interpss.plugin.contingency.con_format.bean.ConBusModEvent;
import org.interpss.plugin.contingency.con_format.bean.ConCase;
import org.interpss.plugin.contingency.con_format.bean.ConEquipAction;
import org.interpss.plugin.contingency.con_format.bean.ConEquipEvent;
import org.interpss.plugin.contingency.con_format.bean.ConEquipMoveEvent;
import org.interpss.plugin.contingency.con_format.bean.ConEquipType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for PSS/E Contingency Definition ({@code .con}) files.
 *
 * <h3>Recognised line patterns (case-insensitive)</h3>
 * <ul>
 *   <li>{@code COM ...}                                          — file comment, skipped</li>
 *   <li>{@code CONTINGENCY 'label' [/'comment'] [CATEGORY 'c']} — start contingency block</li>
 *   <li>{@code DISCONNECT|OPEN|TRIP BUS n}                      — bus isolation event</li>
 *   <li>{@code DISCONNECT|OPEN|TRIP BRANCH|LINE FROM BUS i TO BUS j [CKT id]}        — open 2-terminal branch</li>
 *   <li>{@code DISCONNECT|OPEN|TRIP BRANCH|LINE FROM BUS i TO BUS j TO BUS k [CKT id]} — open 3W transformer</li>
 *   <li>{@code DISCONNECT|OPEN|TRIP THREEWINDING AT BUS i TO BUS j TO BUS k [CKT id]} — open single 3W winding</li>
 *   <li>{@code CLOSE BRANCH|LINE FROM BUS i TO BUS j [CKT id]}  — restore 2-terminal branch</li>
 *   <li>{@code CLOSE BRANCH FROM BUS i TO BUS j TO BUS k [CKT id]} — restore 3W transformer</li>
 *   <li>{@code REMOVE MACHINE|UNIT id FROM BUS n}               — trip generator unit</li>
 *   <li>{@code REMOVE LOAD id FROM BUS n}                       — remove load</li>
 *   <li>{@code REMOVE SHUNT id FROM BUS n}                      — remove fixed shunt</li>
 *   <li>{@code REMOVE SWSHUNT [id] FROM BUS n}                  — remove switched shunt</li>
 *   <li>{@code ADD MACHINE|UNIT id TO BUS n}                    — add generator unit</li>
 *   <li>{@code ADD LOAD|SHUNT|SWSHUNT id TO BUS n}              — add equipment</li>
 *   <li>{@code SET|CHANGE|INCREASE|DECREASE BUS n [GENERATION|LOAD|SHUNT] [TO|BY] r [MW|PERCENT]} — bus-level modification</li>
 *   <li>{@code MOVE r [MW|PERCENT] [ACTIVE|REACTIVE] LOAD|GENERATION|SHUNT FROM BUS i TO BUS j} — load transfer</li>
 *   <li>{@code BLOCK TWOTERMDC id}                              — trip a DC line</li>
 *   <li>{@code CATEGORY 'name'}                                  — start category definition block</li>
 *   <li>{@code VALUE 'name'}                                     — add value to active category block</li>
 *   <li>{@code END}                                              — close active block</li>
 * </ul>
 */
public class ConFileParser {

    private static final Logger log = LoggerFactory.getLogger(ConFileParser.class);

    // -----------------------------------------------------------------------
    // Regex patterns
    // -----------------------------------------------------------------------

    /** {@code CONTINGENCY 'label' [/'comment'] [CATEGORY 'cat']} */
    private static final Pattern CONTINGENCY_PAT = Pattern.compile(
            "CONTINGENCY\\s+'([^']*)'\\s*(?:/\\s*'([^']*)')?\\s*(?:CATEGORY\\s+'([^']*)')?",
            Pattern.CASE_INSENSITIVE);

    /** {@code DISCONNECT|OPEN|TRIP BUS n} */
    private static final Pattern DISC_BUS_PAT = Pattern.compile(
            "(?:DISCONNECT|OPEN|TRIP)\\s+BUS\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code DISCONNECT|OPEN|TRIP BRANCH|LINE FROM BUS i TO BUS j TO BUS k [CIRCUIT|CKT id]}
     * — three-winding: must be tested BEFORE the two-terminal pattern.
     * Also accepts the common data typo {@code FORM} in place of {@code FROM}.
     */
    private static final Pattern DISC_BRANCH_3W_PAT = Pattern.compile(
            "(?:DISCONNECT|OPEN|TRIP)\\s+(?:BRANCH|LINE)\\s+(?:FROM|FORM)\\s+BUS\\s+(\\d+)\\s+TO\\s+BUS\\s+(\\d+)\\s+TO\\s+BUS\\s+(\\d+)(?:\\s+(?:CIRCUIT|CKT)\\s+(\\S+))?",
            Pattern.CASE_INSENSITIVE);

    /** {@code DISCONNECT|OPEN|TRIP BRANCH|LINE FROM BUS i TO BUS j [CIRCUIT|CKT id]} — two-terminal */
    private static final Pattern DISC_BRANCH_2T_PAT = Pattern.compile(
            "(?:DISCONNECT|OPEN|TRIP)\\s+(?:BRANCH|LINE)\\s+(?:FROM|FORM)\\s+BUS\\s+(\\d+)\\s+TO\\s+BUS\\s+(\\d+)(?:\\s+(?:CIRCUIT|CKT)\\s+(\\S+))?",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code CLOSE BRANCH FROM BUS i TO BUS j TO BUS k [CIRCUIT|CKT id]}
     * — three-winding restore; must be tested BEFORE the two-terminal close pattern.
     */
    private static final Pattern CLOSE_BRANCH_3W_PAT = Pattern.compile(
            "CLOSE\\s+(?:BRANCH|LINE)\\s+(?:FROM|FORM)\\s+BUS\\s+(\\d+)\\s+TO\\s+BUS\\s+(\\d+)\\s+TO\\s+BUS\\s+(\\d+)(?:\\s+(?:CIRCUIT|CKT)\\s+(\\S+))?",
            Pattern.CASE_INSENSITIVE);

    /** {@code CLOSE BRANCH|LINE FROM BUS i TO BUS j [CIRCUIT|CKT id]} — two-terminal */
    private static final Pattern CLOSE_BRANCH_2T_PAT = Pattern.compile(
            "CLOSE\\s+(?:BRANCH|LINE)\\s+(?:FROM|FORM)\\s+BUS\\s+(\\d+)\\s+TO\\s+BUS\\s+(\\d+)(?:\\s+(?:CIRCUIT|CKT)\\s+(\\S+))?",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code DISCONNECT|OPEN|TRIP THREEWINDING AT BUS i TO BUS j TO BUS k [CIRCUIT|CKT id]}
     */
    private static final Pattern DISC_3W_WINDING_PAT = Pattern.compile(
            "(?:DISCONNECT|OPEN|TRIP)\\s+THREEWINDING\\s+AT\\s+BUS\\s+(\\d+)\\s+TO\\s+BUS\\s+(\\d+)\\s+TO\\s+BUS\\s+(\\d+)(?:\\s+(?:CIRCUIT|CKT)\\s+(\\S+))?",
            Pattern.CASE_INSENSITIVE);

    /** {@code CATEGORY 'name'} */
    private static final Pattern CATEGORY_PAT = Pattern.compile(
            "CATEGORY\\s+'([^']*)'",
            Pattern.CASE_INSENSITIVE);

    /** {@code VALUE 'name'} */
    private static final Pattern VALUE_PAT = Pattern.compile(
            "VALUE\\s+'([^']*)'",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code REMOVE MACHINE|UNIT|LOAD|SHUNT id FROM BUS n} — equipment removal.
     * Group 1 = keyword (MACHINE|UNIT|LOAD|SHUNT), group 2 = equipId, group 3 = busId.
     */
    private static final Pattern REMOVE_EQUIP_PAT = Pattern.compile(
            "REMOVE\\s+(MACHINE|UNIT|LOAD|SHUNT)\\s+(\\S+)\\s+FROM\\s+BUS\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code REMOVE SWSHUNT [id] FROM BUS n}
     * Group 1 = optional equipId (may be null), group 2 = busId.
     */
    private static final Pattern REMOVE_SWSHUNT_PAT = Pattern.compile(
            "REMOVE\\s+SWSHUNT(?:\\s+(\\S+))?\\s+FROM\\s+BUS\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code ADD MACHINE|UNIT|LOAD|SHUNT id TO BUS n} — equipment add.
     * Group 1 = keyword, group 2 = equipId, group 3 = busId.
     */
    private static final Pattern ADD_EQUIP_PAT = Pattern.compile(
            "ADD\\s+(MACHINE|UNIT|LOAD|SHUNT)\\s+(\\S+)\\s+TO\\s+BUS\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code ADD SWSHUNT [id] TO BUS n}
     * Group 1 = optional equipId, group 2 = busId.
     */
    private static final Pattern ADD_SWSHUNT_PAT = Pattern.compile(
            "ADD\\s+SWSHUNT(?:\\s+(\\S+))?\\s+TO\\s+BUS\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code SET|CHANGE|INCREASE|DECREASE|REDUCE BUS n [GENERATION|LOAD|SHUNT] [TO|BY] r [MW|MVAR|PERCENT]}
     * {@code REDUCE} is treated as a synonym for {@code DECREASE}.
     * Group 1 = verb, group 2 = busId, group 3 = attribute (may be null),
     * group 4 = value, group 5 = unit.
     */
    private static final Pattern BUS_MOD_PAT = Pattern.compile(
            "(SET|CHANGE|INCREASE|DECREASE|REDUCE)\\s+BUS\\s+(\\d+)(?:\\s+(GENERATION|LOAD|SHUNT))?\\s+(?:TO|BY)\\s+([\\d.+-]+)\\s+(MW|MVAR|PERCENT)",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code MOVE r [MW|PERCENT] [ACTIVE|REACTIVE] [LOAD|GENERATION|SHUNT] FROM BUS i TO BUS j}
     * Group 1 = value, group 2 = unit (MW|PERCENT),
     * group 3 = qualifier (ACTIVE|REACTIVE, may be null),
     * group 4 = load type (LOAD|GENERATION|SHUNT),
     * group 5 = fromBusId, group 6 = toBusId.
     */
    private static final Pattern MOVE_LOAD_PAT = Pattern.compile(
            "MOVE\\s+([\\d.]+)\\s+(MW|MVAR|PERCENT)\\s+(?:(ACTIVE|REACTIVE)\\s+)?(LOAD|GENERATION|SHUNT)\\s+FROM\\s+BUS\\s+(\\d+)\\s+TO\\s+BUS\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code BLOCK TWOTERMDC id} — trip a two-terminal DC line.
     * Group 1 = DC line id.
     */
    private static final Pattern BLOCK_DC_PAT = Pattern.compile(
            "BLOCK\\s+TWOTERMDC\\s+(\\S+)",
            Pattern.CASE_INSENSITIVE);

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Parses the given {@code .con} file and returns a {@link ConContainer}.
     *
     * @param filePath path to the PSS/E contingency definition file
     * @return parsed container
     * @throws IOException if the file cannot be read
     */
    public ConContainer parse(Path filePath) throws IOException {
        ConContainer container = new ConContainer(filePath.toAbsolutePath().toString());

        ConCase currentCase = null;   // non-null while inside a CONTINGENCY block
        boolean inCategory  = false;  // true while inside a CATEGORY block

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                // ---- skip empty and COM comment lines ----
                if (trimmed.isEmpty())                          continue;
                if (trimmed.toUpperCase().startsWith("COM"))   continue;

                String upper = trimmed.toUpperCase();

                // ---- END closes the active block ----
                if (upper.equals("END")) {
                    if (currentCase != null) {
                        container.addCase(currentCase);
                        currentCase = null;
                    }
                    inCategory = false;
                    continue;
                }

                // ---- CONTINGENCY header (checked before CATEGORY to avoid false match
                //      when "CONTINGENCY 'X' CATEGORY 'Y'" form is used) ----
                Matcher m = CONTINGENCY_PAT.matcher(trimmed);
                if (m.find()) {
                    // close previous case if not yet closed (malformed file safety)
                    if (currentCase != null) {
                        log.warn("CONTINGENCY '{}' started before END of previous case '{}'",
                                m.group(1), currentCase.getLabel());
                        container.addCase(currentCase);
                    }
                    currentCase = new ConCase(m.group(1));
                    if (m.group(2) != null) currentCase.setComment(m.group(2).trim());
                    if (m.group(3) != null) currentCase.setCategory(m.group(3).trim());
                    continue;
                }

                // ---- CATEGORY definition block ----
                m = CATEGORY_PAT.matcher(trimmed);
                if (m.find()) {
                    inCategory = true;
                    container.addCategory(m.group(1));
                    continue;
                }

                if (inCategory) {
                    m = VALUE_PAT.matcher(trimmed);
                    if (m.find()) {
                        container.addCategory(m.group(1));
                    }
                    // other lines inside CATEGORY block are ignored
                    continue;
                }


                if (currentCase == null) {
                    log.warn("Event record outside CONTINGENCY block, skipping: {}", trimmed);
                    continue;
                }

                // DISCONNECT BUS
                m = DISC_BUS_PAT.matcher(trimmed);
                if (m.find()) {
                    currentCase.addBusEvent(new ConBusEvent(Integer.parseInt(m.group(1))));
                    continue;
                }

                // DISCONNECT BRANCH 3W (before 2T!)
                m = DISC_BRANCH_3W_PAT.matcher(trimmed);
                if (m.find()) {
                    currentCase.addBranchEvent(new ConBranchEvent(
                            ConBranchAction.DISCONNECT,
                            Integer.parseInt(m.group(1)),
                            Integer.parseInt(m.group(2)),
                            Integer.parseInt(m.group(3)),
                            m.group(4) != null ? m.group(4) : "1"));
                    continue;
                }

                // DISCONNECT BRANCH 2T
                m = DISC_BRANCH_2T_PAT.matcher(trimmed);
                if (m.find()) {
                    currentCase.addBranchEvent(new ConBranchEvent(
                            ConBranchAction.DISCONNECT,
                            Integer.parseInt(m.group(1)),
                            Integer.parseInt(m.group(2)),
                            m.group(3) != null ? m.group(3) : "1"));
                    continue;
                }

                // DISCONNECT THREEWINDING (single winding)
                m = DISC_3W_WINDING_PAT.matcher(trimmed);
                if (m.find()) {
                    currentCase.addBranchEvent(new ConBranchEvent(
                            ConBranchAction.DISCONNECT_3W_WINDING,
                            Integer.parseInt(m.group(1)),
                            Integer.parseInt(m.group(2)),
                            Integer.parseInt(m.group(3)),
                            m.group(4) != null ? m.group(4) : "1"));
                    continue;
                }

                // CLOSE BRANCH 3W (before 2T!)
                m = CLOSE_BRANCH_3W_PAT.matcher(trimmed);
                if (m.find()) {
                    currentCase.addBranchEvent(new ConBranchEvent(
                            ConBranchAction.CLOSE,
                            Integer.parseInt(m.group(1)),
                            Integer.parseInt(m.group(2)),
                            Integer.parseInt(m.group(3)),
                            m.group(4) != null ? m.group(4) : "1"));
                    continue;
                }

                // CLOSE BRANCH 2T
                m = CLOSE_BRANCH_2T_PAT.matcher(trimmed);
                if (m.find()) {
                    currentCase.addBranchEvent(new ConBranchEvent(
                            ConBranchAction.CLOSE,
                            Integer.parseInt(m.group(1)),
                            Integer.parseInt(m.group(2)),
                            m.group(3) != null ? m.group(3) : "1"));
                    continue;
                }

                // REMOVE MACHINE|UNIT|LOAD|SHUNT id FROM BUS n
                m = REMOVE_EQUIP_PAT.matcher(trimmed);
                if (m.find()) {
                    String kw = m.group(1).toUpperCase();
                    ConEquipType equipType = kw.equals("LOAD")  ? ConEquipType.LOAD
                                           : kw.equals("SHUNT") ? ConEquipType.SHUNT
                                           :                      ConEquipType.MACHINE; // MACHINE or UNIT
                    currentCase.addEquipEvent(new ConEquipEvent(
                            ConEquipAction.REMOVE, equipType, m.group(2), Integer.parseInt(m.group(3))));
                    continue;
                }

                // REMOVE SWSHUNT [id] FROM BUS n
                m = REMOVE_SWSHUNT_PAT.matcher(trimmed);
                if (m.find()) {
                    currentCase.addEquipEvent(new ConEquipEvent(
                            ConEquipAction.REMOVE, ConEquipType.SWSHUNT,
                            m.group(1),                  // may be null
                            Integer.parseInt(m.group(2))));
                    continue;
                }

                // ADD MACHINE|UNIT|LOAD|SHUNT id TO BUS n
                m = ADD_EQUIP_PAT.matcher(trimmed);
                if (m.find()) {
                    String kw2 = m.group(1).toUpperCase();
                    ConEquipType addType = kw2.equals("LOAD")  ? ConEquipType.LOAD
                                        : kw2.equals("SHUNT") ? ConEquipType.SHUNT
                                        :                      ConEquipType.MACHINE;
                    currentCase.addEquipEvent(new ConEquipEvent(
                            ConEquipAction.ADD, addType, m.group(2), Integer.parseInt(m.group(3))));
                    continue;
                }

                // ADD SWSHUNT [id] TO BUS n
                m = ADD_SWSHUNT_PAT.matcher(trimmed);
                if (m.find()) {
                    currentCase.addEquipEvent(new ConEquipEvent(
                            ConEquipAction.ADD, ConEquipType.SWSHUNT,
                            m.group(1),
                            Integer.parseInt(m.group(2))));
                    continue;
                }

                // SET|CHANGE|INCREASE|DECREASE|REDUCE BUS n [attr] [TO|BY] r [MW|PERCENT]
                m = BUS_MOD_PAT.matcher(trimmed);
                if (m.find()) {
                    String verb = m.group(1).toUpperCase();
                    ConBusModAction busModAction = verb.equals("SET")      ? ConBusModAction.SET
                                                 : verb.equals("CHANGE")   ? ConBusModAction.CHANGE
                                                 : verb.equals("INCREASE") ? ConBusModAction.INCREASE
                                                 :                           ConBusModAction.DECREASE; // DECREASE or REDUCE
                    currentCase.addBusModEvent(new ConBusModEvent(
                            busModAction,
                            Integer.parseInt(m.group(2)),
                            m.group(3) != null ? m.group(3).toUpperCase() : null,
                            Double.parseDouble(m.group(4)),
                            m.group(5).toUpperCase()));
                    continue;
                }

                // MOVE r [MW|PERCENT] [ACTIVE|REACTIVE] LOAD|GENERATION|SHUNT FROM BUS i TO BUS j
                m = MOVE_LOAD_PAT.matcher(trimmed);
                if (m.find()) {
                    String qualifier = m.group(3);
                    String loadType  = qualifier != null
                            ? qualifier.toUpperCase() + " " + m.group(4).toUpperCase()
                            : m.group(4).toUpperCase();
                    currentCase.addEquipMoveEvent(new ConEquipMoveEvent(
                            Double.parseDouble(m.group(1)),
                            m.group(2).toUpperCase(),
                            loadType,
                            Integer.parseInt(m.group(5)),
                            Integer.parseInt(m.group(6))));
                    continue;
                }

                // BLOCK TWOTERMDC id
                m = BLOCK_DC_PAT.matcher(trimmed);
                if (m.find()) {
                    // Represent as a ConEquipEvent with busId = -1 (not bus-attached)
                    currentCase.addEquipEvent(new ConEquipEvent(
                            ConEquipAction.BLOCK, ConEquipType.DC_LINE, m.group(1), -1));
                    continue;
                }

                // Unrecognised line inside a contingency block — log and skip
                log.warn("Unrecognised event line in contingency '{}', skipping: {}",
                        currentCase.getLabel(), trimmed);
            }
        }

        // Handle file that ends without a final END
        if (currentCase != null) {
            log.warn("File ended without END for contingency '{}'", currentCase.getLabel());
            container.addCase(currentCase);
        }

        log.info("Parsed {} contingency cases from {}", container.getCases().size(), filePath.getFileName());
        return container;
    }
}
