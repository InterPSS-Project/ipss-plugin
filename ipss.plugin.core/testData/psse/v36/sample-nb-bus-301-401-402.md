# PSS®E Sample Case — Bus / Branch Connections for 301, 401, 402

Connectivity of the three buses that have **no node-breaker overlay** in [`sample_nb.raw`](sample_nb.raw):

| Bus | Name | kV | IDE | Area | Zone | NB overlay |
|----:|------|---:|----:|-----:|-----:|------------|
| 301 | NORTH | 765 | 3 (swing) | 3 `CENTRAL_DC` | 5 | none |
| 401 | COGEN-1 | 500 | 3 (swing) | 4 `EAST_COGEN1` | 9 | none |
| 402 | COGEN-2 | 500 | 3 (swing) | 6 `EAST_COGEN2` | 9 | none |

Related: [`sample-nb-substation-nbModel.md`](sample-nb-substation-nbModel.md) (station containment; these three buses are the “no NB” set).

---

## Summary

**None of 301 / 401 / 402 appear in BRANCH or TRANSFORMER data.**  
Their only network ties into the AC grid are **DC links**:

| Bus | AC branch | Transformer | Two-terminal DC | Multi-terminal DC | Generators |
|----:|:---------:|:-----------:|:---------------:|:-----------------:|-----------:|
| 301 | — | — | `TWO_TERM_DC1` → 3021, `TWO_TERM_DC2` → 3022 | — | 3 (`1`,`2`,`3`) |
| 401 | — | — | — | `MULTERM_DC_1` (DC bus 1) | 1 (`1`) |
| 402 | — | — | — | `MULTERM_DC_1` (DC bus 3) | 1 (`1`) |

```
                    ┌─────────────────────────────────────────┐
                    │  AC grid (has NB overlay on far buses)  │
                    └─────────────────────────────────────────┘
                         │                         │
              xfmr T4 / T5 @ 152          AC branches @ 205 / 214
                         │                         │
                      3021, 3022                212, 213
                         │                         │
              TWO_TERM_DC1/2                 MULTERM_DC_1
                         │                         │
                        301                   401, 402
                   (no AC branch)            (no AC branch)
```

---

## Bus 301 — NORTH (765 kV)

### Local equipment

| Kind | Id | Notes |
|------|----|-------|
| Generator | `301` / `1` | PG≈996.9 MW, renewable Q mode |
| Generator | `301` / `2` | PG≈996.9 MW |
| Generator | `301` / `3` | PG≈996.9 MW |
| Area swing | Area 3 | `ISW=301`, PDES=2900 MW |

### Network connections (DC only)

| Link | From (rectifier IPR) | To (inverter IPI) | Far AC path |
|------|---------------------:|------------------:|-------------|
| `TWO_TERM_DC1` | **301** | **3021** WDUM | 3021 —xfmr `T4`→ 152 MID500 |
| `TWO_TERM_DC2` | **301** | **3022** EDUM | 3022 —xfmr `T5`→ 152 MID500 |

```
  [gens 1,2,3]
       │
     Bus 301  NORTH  765 kV   ← no AC branch / no xfmr / no NB nodes
       │
       ├──── TWO_TERM_DC1 ──── Bus 3021 WDUM 18 kV ── xfmr T4 ── Bus 152
       │
       └──── TWO_TERM_DC2 ──── Bus 3022 EDUM 18 kV ── xfmr T5 ── Bus 152
```

Far-side AC (for orientation only; not incident on 301):

| Far bus | Connection into AC grid |
|--------:|-------------------------|
| 3021 | Transformer `152–3021` ckt `T4` (“WDUM DC”) |
| 3022 | Transformer `152–3022` ckt `T5` (“EDUM DC”) |
| 152 | MID500 — hub into the 500 kV AC mesh |

---

## Bus 401 — COGEN-1 (500 kV)

### Local equipment

| Kind | Id | Notes |
|------|----|-------|
| Generator | `401` / `1` | PG=321 MW, infeed Q-constant |
| Area swing | Area 4 | `ISW=401`, PDES=300 MW |

### Network connections (multi-terminal DC only)

Part of **`MULTERM_DC_1`** (4 converters, 5 DC buses, 4 DC lines):

| Role | AC bus | DC bus id | DC name | SETVL (MW) |
|------|-------:|----------:|---------|------------|
| Converter | **401** | 1 | `DC1` | +321 |
| Converter | 212 INVERT1 | 2 | `DC2` | +500 |
| Converter | **402** | 3 | `DC3` | +321 |
| Converter | 213 INVERT2 | 4 | `DC4` | −303.8 |
| Ground / stub | (no AC) | 5 | `DC5` | — |

DC line topology (all RDC=29 Ω, star into DC5):

```
  DC1 (401) ──┐
  DC2 (212) ──┼── DC5 (ground node)
  DC3 (402) ──┤
  DC4 (213) ──┘
```

```
  [gen 1]
      │
    Bus 401  COGEN-1  500 kV   ← no AC branch / no xfmr / no NB nodes
      │
      └── MULTERM_DC_1 ──► DC1 ──► DC5 ◄── DC2 ◄── Bus 212 ◄── branch 205–212 '1'
                                      ▲
                                      └── DC4 ◄── Bus 213 ◄── branch 213–214 '1'
                                      ▲
                                      └── DC3 ◄── Bus 402 (see below)
```

Far-side AC (incident on 212 / 213, not on 401):

| Far bus | AC branch into grid |
|--------:|---------------------|
| 212 INVERT1 | `205–212` ckt `1` |
| 213 INVERT2 | `213–214` ckt `1` (214 also ties to 205 via `205–214` ckt `2`) |

---

## Bus 402 — COGEN-2 (500 kV)

### Local equipment

| Kind | Id | Notes |
|------|----|-------|
| Generator | `402` / `1` | PG=321 MW, infeed Q-constant |
| Area swing | Area 6 | `ISW=402`, PDES=300 MW |

### Network connections

Same multi-terminal DC as 401: converter on AC bus **402** ↔ DC bus **3** (`DC3`) ↔ star node **DC5**.

```
  [gen 1]
      │
    Bus 402  COGEN-2  500 kV   ← no AC branch / no xfmr / no NB nodes
      │
      └── MULTERM_DC_1 ──► DC3 ──► DC5 ──► (shared with 401, 212, 213)
```

There is **no direct AC or DC edge between 401 and 402**; they only meet at the common DC star `DC5`.

---

## Combined connection map

```
                         AC mesh
                            │
              ┌─────────────┼─────────────┐
              │             │             │
            Bus 152       Bus 205       Bus 214
           (MID500)      (SUB230)      (LOADER)
              │             │             │
         xfmr T4 / T5    br 205-212    br 213-214
              │             │             │
          3021 / 3022     Bus 212       Bus 213
              │          INVERT1       INVERT2
              │             │             │
        TWO_TERM_DC1/2      └──────┬──────┘
              │                    │
           Bus 301            MULTERM_DC_1
           NORTH                 (DC5 hub)
                                 /      \
                             Bus 401  Bus 402
                            COGEN-1  COGEN-2
```

---

## Implications for InterPSS / NB import

1. Importer assigns `bus.substation` only from SUBSTATION NODE bus `I` — so `Bus301`, `Bus401`, `Bus402` stay **without** a substation (see comment in `PSSE_Sample_NB_Sample`).
2. After loadflow / topology, these buses still participate via **HVDC** models (two-terminal and multi-terminal), not via AC `AclfBranch`.
3. Disabling `Bus401` / `Bus402` (as in the sample’s commented experiments) removes the cogen infeeds and their MTDC converters, not an AC radial path.
