# Future State What-If Analysis

A forward-looking security analysis capability that evaluates grid safety under projected future conditions rather than the current real-time state. Also called **rolling what-if simulation** (推演分析) or **future-state simulation** — Innovation 4 of the sec-order Digital Twin platform.

## Problem and Role

Traditional offline simulation starts from a static snapshot and runs in isolation from the live grid. The DT platform instead uses the **real-time mirrored model** as its foundation, superimposes planned future information, and runs the **same security assessment pipeline** used for live operations across many future timepoints. This closes the loop between day-ahead planning and real-time monitoring on a single unified model.

Within the dual-path architecture, what-if analysis runs on the **seconds path** (Grid DT Model + CEP Engine), not the legacy minutes-path periodic DSA batch.

## Future State Model

A **future state** (未来态) is a forward-looking DT variant constructed by superimposing three classes of forecast/plan data onto the current real-time Grid DT Model:

| Input | Purpose |
|---|---|
| **Generation schedules** | Planned unit output, exchanges, DC transfers |
| **Load forecasts** | Expected demand at each future timepoint |
| **Maintenance plans** | Planned outages, switching, equipment unavailability |

The result is a sequence of virtual grid operating states — one per timepoint — each ready for loadflow, rule check, and risk identification.

This is distinct from generic offline simulation: the starting topology, switching state, and model mapping come from the **live DT mirror**, ensuring consistency with what operators see on the monitor.

## Analysis Pipeline

For each future timepoint, the rolling simulation executes:

1. **Mode construction** — apply schedules, forecasts, and outage plans to build the future-state model instance
2. **Loadflow** — solve the AC power flow for that timepoint
3. **Rule check** — evaluate all digitized stability/operation rules (3,645 control rules across 1,575 modes in the Hunan deployment)
4. **Risk identification** (optional per workflow) — contingency-based topological analysis with Grade I–VI classification

The pipeline reuses the same [Grid DT Model](grid-dt-model.md), [Knowledge Model](knowledge-model.md), and [InterPSS](interpss.md) simulation engine as real-time assessment — only the input data changes per timepoint.

```
Real-time DT Model
       +
Gen schedules + Load forecasts + Maintenance plans
       ↓
Future-state model (per timepoint)
       ↓
Loadflow → Rule check → Risk ID
       ↓
Security report / adjustment guidance
```

## Performance

Enabled by three second-order IT technologies working together:

- **[Graph computing](graph-computing.md)** — efficient topology traversal and mode construction
- **[In-memory computing](in-memory-computing.md)** — zero disk I/O; algorithms run where data lives
- **[Parallel computing](parallel-computing.md)** — concurrent analysis across multiple timepoints (case-level IMPP + algorithm-level PAE)

| Metric | Value | Context |
|---|---|---|
| Day-ahead horizon | 96 timepoints | 15-minute resolution × 24 hours |
| Full pipeline time | ~3 sec (3.68 sec avg) | Mode construction + loadflow + rule check |
| Test grid | 4,383-node provincial grid | Third-party verification |
| Speedup vs prior approach | >80× | Previous methods: 5+ minutes |
| Single-timepoint risk ID | 10 ms | vs ~1 minute previously (6,000×) |

For comparison, the legacy D5000 DSA round trip on the minutes path takes ~10 minutes.

## Use Cases

### Operational schedule refinement
Operators run rolling simulations to iteratively adjust generation schedules, maintenance timing, or load transfers until all timepoints pass security rules. The sandbox enables "try before commit" without affecting live operations.

### Proactive risk identification
Security issues that would materialize hours or days ahead are surfaced before they occur — overload risks, rule violations, unstable operating modes. Hunan Grid identified and eliminated **100+ overload risks** through this workflow.

### Market and planning verification
- **Spot market clearing verification** — proposed market schedules are evaluated for security before execution (related to [DT Market Clearing](dt-market-clearing.md))
- **Day/week/month/year plan simulation** — longer-horizon planning with the same DT infrastructure

### Emergency response
Pre-built contingency models can be matched and evaluated in real-time during incidents. Hunan Grid's **2024 Chinese New Year anti-icing** response used emergency simulations to transfer 120 MW load and compile contingency plans during extreme freezing rain.

## Production Evidence

| Deployment | Since | What-If Activity |
|---|---|---|
| **Hunan Grid** | Jan 2020 | 1,500+ what-if simulations; Qi-Shao DC transmission capacity ↑22% |
| **Hubei Grid** | Mar 2023 | Automated generation/load balance and outage safety verification; shift efficiency ↑80%+ |
| **Inner Mongolia** | Jul 2022 | Day-ahead and operational plan verification |

## Validation

A key quality metric is **accuracy comparison between day-ahead predictions and next-day actual measurements** — how well the future-state model's security assessment matches what actually happens when the forecasted time arrives.

## Terminology (GB/T Draft)

Proposed standard definitions from production DT experience (see [Glossary Standard Recommendations](../../notes/glossary-standard-recommendations.md)):

- **推演分析 / What-If Analysis**: Security analysis method that uses the real-time mirrored model as foundation, superimposes future information, and continuously simulates operating state at multiple future timepoints.
- **未来态 / Future State**: Forward-looking virtual grid operating state constructed by superimposing generation schedules, load forecasts, and maintenance plans onto the real-time mirror model.

## Related Applications

- **Short-term trend warning** (planned): Future-state DT + CEP → early warning of approaching violations before they occur in real time
- **Sensitivity-based adjustment guidance**: Rule evaluation results indicate which schedule parameters to adjust

## Related

- [Overview](Digital%20Twin.md)
- [Project Summary](project-summary.md) — Innovation 4; benchmark comparisons
- [Grid DT Architecture](grid-dt-architecture.md) — dual-path architecture; performance table
- [Grid DT Model](grid-dt-model.md) — three-layer model; forecast superposition input
- [Knowledge Model](knowledge-model.md) — rolling what-if as future application
- [Rule-Based Security Assessment](rule-based-security-assessment.md) — same rule pipeline on future states
- [Risk Identification](risk-identification.md) — contingency analysis per timepoint
- [Graph Risk Identification](graph-risk-identification.md) — hybrid topology graph for fast risk ID
- [DT Market Clearing](dt-market-clearing.md) — schedule verification via DT
- [In-Memory Computing](in-memory-computing.md)
- [Graph Computing](graph-computing.md)
- [Parallel Computing](parallel-computing.md)
- [Glossary Standard Recommendations](../../notes/glossary-standard-recommendations.md) — proposed 推演分析 and 未来态 definitions

Source: Project summary (2025 CSEE award nomination), deployment questionnaire, glossary gap analysis, and related wiki pages synthesized from CEPRI DT platform documentation.
