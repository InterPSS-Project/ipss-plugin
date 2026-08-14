# Texas2k Substation Data Consistency

**Case:** `ipss-plugin/ipss.plugin.core/testData/psse/nbreaker/Texas2k_series24_case3_2024summerpeak.RAW`  
**Spec:** [substation-data.md](./substation-data.md) (PSS®E 36.1 §1.33)  
**RAW revision:** 34  
**Date analyzed:** 2026-07-29

## Verdict

Structurally and cross-referentially consistent as a **station grouping + equipment-attachment overlay**, but **not a usable node-breaker topology**: zero station switching devices, and every node shares the placeholder name `newbus 138`.

| Aspect | Result |
|--------|--------|
| Block structure, IS range, geo/SRG | Pass |
| Node↔bus 1:1, terminal cross-refs, full equipment coverage | Pass |
| Node name uniqueness within substation | **Fail** |
| Station switching devices | **Empty** (0 breakers/disconnects) |

## Inventory

| Layer | Count | In substation model |
|-------|------:|---------------------|
| Substations | 1,250 | IS 1…1250, sequential, no gaps |
| Buses | 2,000 | 2,000 nodes (100%, 1:1) |
| Nodes | 2,000 | — |
| Station switches | — | **0** |
| Equipment terminals | 10,072 | see type breakdown below |
| Loads | 1,350 | 1,350 `L` terminals |
| Machines | 743 | 743 `M` terminals |
| Fixed shunts | 4 | 4 `F` terminals |
| Switched shunts | 153 | 153 `S` terminals |
| AC branches | 2,854 | 5,708 `B` (both ends) |
| 2W transformers | 1,057 | 2,114 type `2` (both ends) |
| 3W transformers | 0 | — |

### Terminals by type

| Type | Count |
|------|------:|
| Branch `B` | 5,708 |
| XFMR `2` | 2,114 |
| Load `L` | 1,350 |
| Machine `M` | 743 |
| Switched shunt `S` | 153 |
| Fixed shunt `F` | 4 |

Branch and transformer terminals are recorded at **both** ends (0 single-ended devices).

## Rule-by-rule results

Checks against [substation-data.md](./substation-data.md):

| Rule | Result | Detail |
|------|--------|--------|
| Block structure (sub → nodes → switches → terminals) | Pass | 1,250 complete blocks; file ends with substation `0` + `Q` |
| Substation `IS` unique & in 1–99999 | Pass | Sequential 1…1250, no gaps or duplicates |
| `LATI` / `LONG` / `SRG` ranges | Pass | Lat 25.91…35.83, Lon −104.62…−94.37 (Texas); SRG 0.01…16 Ω, none zero; no (0,0) placeholders |
| Node `NI` unique per station; bus `I` exists | Pass | 2,000 nodes ↔ 2,000 buses, 1:1, no missing buses |
| Node `NAME` unique within substation | **Fail** | All 2,000 nodes named `newbus 138`; 266 multi-node stations violate uniqueness |
| Switching devices (`NI`/`NJ` in station, `X` ≠ 0) | N/A — empty | Every switch list is only the terminator `0` |
| Terminal `NI` matches bus’s node in station | Pass | 0 NI mismatches; no `NI=0` on modeled buses |
| Terminal equipment resolves in bus-branch data | Pass | Loads/gens/shunts/branches/2W XFMRs: 0 dangling pointers |
| Equipment coverage (every device has a terminal) | Pass | Full coverage as in inventory table |
| Bus not claimed by multiple substations | Pass | Each electrical bus appears in exactly one station |
| Duplicate terminal records | Pass | 0 true duplicate terminal keys |

## Station size distribution

### Nodes per substation

| Nodes | Substations |
|------:|------------:|
| 1 | 984 |
| 2 | 115 |
| 3 | 64 |
| 4 | 15 |
| 5 | 20 |
| 6 | 11 |
| 7 | 12 |
| 8 | 10 |
| 9 | 7 |
| 10 | 4 |
| 11 | 2 |
| 12 | 3 |
| 13 | 1 |
| 14 | 1 |
| 16 | 1 |

- **984** single-bus stations  
- **266** multi-bus stations — **all** span multiple base kV (e.g. 115/230, 13.8/115); none are same-voltage multi-section buses without switches

### Largest multi-bus stations

| IS | Name | Nodes | Terminals | Bus range |
|---:|------|------:|----------:|-----------|
| 1105 | THOMPSONS | 16 | 56 | 7346–7361 |
| 1110 | CHANNELVIEW 1 | 14 | 55 | 7366–7379 |
| 984 | LAPORTE | 13 | 46 | 7130–7142 |
| 998 | HOUSTON 5 | 12 | 56 | 7159–7170 |
| 1010 | HOUSTON 4 | 12 | 59 | 7186–7197 |
| 973 | DEER PARK | 12 | 44 | 7104–7115 |
| 46 | ODESSA 1 | 11 | 70 | 1071–1081 |
| 858 | ELMENDORF | 11 | 40 | 6239–6249 |

## Geo / grounding

| Field | Min | Max | Notes |
|-------|-----|-----|-------|
| `LATI` °N | 25.913 | 35.831 | Texas band |
| `LONG` °E | −104.625 | −94.367 | No (0,0) placeholders |
| `SRG` Ω | 0.01 | 16.0 | No zeros; PSS/E default would be 0.1 |

## Findings for InterPSS NB import

### P1 — Duplicate node names

Spec requires node `NAME` unique within the substation. Here every node is literally `newbus 138`.

InterPSS node ids use `NI` + substation name (`NBNode_{isub}-{ni}@{subName}`), so import can still key nodes, but any name-based lookup or RAWX round-trip will collide in the 266 multi-node stations.

### P1 — Empty switching layer

All 266 multi-node stations have distinct electrical buses and **no** switches — correct for a voltage-level grouping, not for breaker topology.

Closed-switch merge, loop-flow via switch `X`, NB→ZBR conversion, and stuck-breaker paths remain **untestable** on this case.

### OK — Cross-references clean

Every terminal pointer resolves; every network device has a terminal; no bus is shared across substations.

Safe for `PSSESubstationImporter` overlay tests: station / bus / equipment maps should load without dangling refs.

## What this model is

Each substation groups one or more electrical buses (voltage levels) and pins every attached device to a node. Branch and transformer terminals are recorded at both ends.

Because there are no breakers/disconnects:

- Closed-switch merge cannot be exercised  
- Loop-flow via switch `X` cannot be exercised  
- True breaker contingencies cannot be exercised from this data alone  

## Related

- [substation-data.md](./substation-data.md) — Substation Data Group field reference  
- [SubstationNBreaker-architecture.md](../../ipss.core_EMF/docs/md/SubstationNBreaker-architecture.md) — InterPSS NB overlay design  
