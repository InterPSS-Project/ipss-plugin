# DCLF Contingency Analysis

## Custom Branch Ratings

Contingency analysis uses Rating B by default for monitored branch thermal-limit
checks. A study can override selected monitored-branch ratings with a JSON or
CSV file and leave all other branches on Rating B.

CSV format:

```csv
branch_id,rating_mva
Bus2->Bus5(1),75.5
Bus4->Bus7(1),120.0
```

CSV can also be keyed by branch external UID:

```csv
extUID,rating_mva
LN-000245,75.5
LN-000902,120.0
```

JSON array format:

```json
{
  "branch_ratings": [
    { "branch_id": "Bus2->Bus5(1)", "rating_mva": 75.5 },
    { "extUID": "LN-000902", "rating_mva": 120.0 }
  ]
}
```

JSON map format is also accepted:

```json
{
  "Bus2->Bus5(1)": 75.5,
  "Bus4->Bus7(1)": 120.0
}
```

Use the imported ratings through the standard `DclfContingencyConfig`:

```java
DclfContingencyConfig config = new DclfContingencyConfig();
config.setCustomBranchRatings(
        ContingencyFileUtil.importBranchRatings(new File("branch-ratings.csv")));

ConcurrentLinkedQueue<BranchCAResultRec> results =
        ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(
                net, contingencies, monitoredBranchIds, config, 1);
```

For a file that is entirely keyed by branch `extUID`, use the provider key-mode
flag:

```java
config.setBranchRatingProvider(
        ContingencyFileUtil.importBranchRatingProvider(new File("branch-ratings.csv"), true));
```

For mixed files containing some `branch_id` rows and some `extUID` rows, resolve
the file against the network once before configuring the analyzer:

```java
config.setCustomBranchRatings(
        ContingencyFileUtil.importBranchRatings(net, new File("branch-ratings.json")));
```

If a monitored branch is not present in the custom rating file, the core
`BranchRatingProvider` logs one warning for that branch id and falls back to
the branch model's Rating B. The warning is emitted when the analyzer compiles
the monitored branch data, not inside the per-contingency numeric loop.

## Contingency Pre-Screening

Large DCLF studies can pre-screen a JSON contingency file before running full
violation analysis:

```java
ContingencyPreScreenReport report =
        ContingencyFileUtil.preScreenDclfContingencies(net, new File("contingencies.json"));
```

The scanner imports the same JSON contingency definitions used by the analyzer,
runs base DCLF once, and classifies every contingency into four buckets:

| Bucket | Meaning |
|---|---|
| `ISLANDING` | OPEN/CLOSE topology creates one or more active bus islands disconnected from the network reference bus. |
| `E_PTDF_SINGULAR` | Topology remains connected, but the MLODF `[E-PTDF]` matrix is singular or compacted, so special handling is needed. |
| `NORMAL` | No topology islanding and the MLODF `[E-PTDF]` matrix is full-rank. |
| `UNSUPPORTED` | The contingency cannot be resolved or contains unsupported action/object types. |

Each result includes action counts, OPEN/CLOSE counts, island count and island
bus counts, plus the original and effective `[E-PTDF]` matrix size. Aggregate
counts are available from `ContingencyPreScreenReport`:

```java
int islanding = report.islandingContingencies();
int singular = report.ePtdfSingularContingencies();
int normal = report.normalContingencies();
```

The topology check is graph-based and does not mutate the network. It applies
CLOSE actions as temporary edges, removes OPEN actions as temporary edge
removals, and counts components outside the reference-bus component.
