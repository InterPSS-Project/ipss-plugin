# From Parser Bugs to Repeatable QA: Updating InterPSS Three-Phase Studies for OpenDSS-Scale Feeders

Large-scale distribution studies are rarely blocked by one dramatic algorithmic
bug. More often, progress slows down in the unglamorous middle: one linecode
unit is off, one transformer form is interpreted differently, a load model has a
low-voltage fallback rule, a regulator tap is represented one way in one tool
and another way in another tool. Every individual mismatch looks small. Together
they can make a feeder fail to converge, or worse, converge to a result that is
quietly different from the reference.

This is the story of how we updated the InterPSS three-phase distribution stack
to support larger OpenDSS feeders and benchmark against DSS-Python/OpenDSS. More
importantly, it is a story about turning one-off debugging experience into a
reusable engineering skill: a documented workflow that Codex can apply again on
the next difficult circuit.

Although the work described here is about distribution power flow, the pattern
is much broader. Transmission planning, EMT studies, protection studies, CIM or
PSSE mapping, PSCAD/EMTDC model translation, and production-grade planning
automation all face the same kind of problem: the expensive part is often not
running the solver. It is aligning data, device semantics, controls, units, and
reference behavior across tools.

## The Starting Point

InterPSS already had a three-phase distribution model and an OpenDSS parser, but
the early parser was mostly sufficient for smaller or simplified cases. Once we
started testing against larger feeders, especially IEEE 8500, IEEE 123, EPRI
Ckt24, and Ckt7, the work changed character.

The problem was no longer "can we parse a feeder?" It became:

- Can we parse the feeder without silently changing the physics?
- Can fixed-point power flow converge on the same network that DSS-Python
  solves?
- If both converge, are bus voltages close phase by phase?
- If voltages differ, is the mismatch from a solver issue, a device model issue,
  a control state issue, or an input-data interpretation issue?
- Can the next difficult feeder be debugged faster than the last one?

That last question became important. A one-off fix is useful. A repeatable QA
process is much more valuable.

## Why OpenDSS Benchmarking Is Hard

OpenDSS is a practical engineering tool with many device forms and scripting
conveniences. Supporting realistic feeders means supporting more than the
obvious objects.

During this work, mismatches came from many places:

- Linecode and line-geometry conversion, including units such as feet, kft, and
  miles.
- Triplex and secondary service models.
- Center-tap transformers and no-load admittance.
- Transformer `%r` and `%rs` winding resistance interpretation.
- Regulator tap ratios and per-phase single-phase regulator banks.
- Capacitor initial states under controls-off studies.
- Circuit source Thevenin impedance.
- Load defaults, allocation factors, CVR behavior, ZIPV coefficients, and
  low-voltage fallback behavior.
- Islanding and near-zero busbar impedance.
- Phase masks, inactive phases, and single-phase devices embedded in
  three-phase data structures.

None of these items is exotic. They are exactly the details that appear in real
feeders. The challenge is that each detail has to match the reference tool well
enough for the full-system result to be meaningful.

## The Iteration Loop

The core workflow became a loop:

1. Run InterPSS fixed-point power flow and compare against DSS-Python.
2. Export voltage differences by bus, phase, and source depth.
3. Look for a pattern: downstream accumulation, phase-specific bias, local
   spikes, or source-level offset.
4. Trace the source-to-worst-bus path.
5. Compare branch voltage drops, currents, Yprim blocks, KCL, and KVL.
6. Reduce the suspected device behavior to a mini-case.
7. Compare the mini-case against DSS-Python.
8. Fix the parser or model.
9. Promote the mini-case into a regression test.
10. Record the lesson in the QA skill.

This loop sounds simple, but it changed the work. Instead of staring at one max
voltage error and guessing, we could progressively localize the mismatch.

For IEEE 8500, depth plots showed that the mismatch was not a universal voltage
bias. Some phase differences were positive, others negative. That pushed us away
from a global base-voltage explanation and toward device-level modeling. Later,
source-path branch-drop diagnostics narrowed the attention to specific branches
and downstream regions. For Ckt24, the plot looked different: a large mismatch
appeared right near the source-depth-zero transformer, which pointed toward
source and transformer modeling rather than downstream accumulation.

The process matters because the same top-level symptom, "voltage differs from
OpenDSS," can have many causes. The plot and path diagnostics help decide where
not to waste time.

## Mini-Cases Became the Proof

Broad feeder comparisons are excellent smoke tests, but they are poor proof of a
device model. A full feeder has too many overlapping effects.

So we created mini-cases:

- Source plus center-tap transformer plus load.
- Low-voltage center-tap loads to test `Vminpu` fallback.
- Constant impedance load cases.
- Delta and split-phase load cases.
- OpenDSS load models 1 through 8 at low voltage.
- Tests designed to compare directly against DSS-Python references.

These mini-cases did two things. First, they gave us confidence that a specific
device behavior matched OpenDSS. Second, they protected against regression. Once
a modeling gap was fixed, it became much harder to accidentally reintroduce it.

The most recent load-model work is a good example. Formula tests were useful,
but not enough. We added a DSS-Python-backed mini feeder with a stiff 0.90 pu
source and OpenDSS load models 1 through 8 side by side. That test stresses
low-voltage behavior and compares actual solved voltages against DSS-Python, not
just local formulas.

## Turning Debugging Into a Skill

At some point, the workflow itself became the product.

We created an `opendss-device-qa` skill that captures the repeatable debugging
ladder:

- Align the reference case.
- Establish the global voltage-error signal.
- Separate convergence artifacts from modeling artifacts.
- Localize by topology and source path.
- Compare device physics before solver logic.
- Check KCL and KVL.
- Audit injections, controls, and device states.
- Stress uncertain assumptions with toggles.
- Reduce to a mini-case.
- Capture durable evidence.

This skill was not created in one shot. It was distilled from repeated
interactions: failures, partial fixes, wrong hypotheses, better diagnostics, and
new mini-tests. Once documented, it became reusable. When we moved from IEEE
8500 to Ckt24 and then Ckt7, we did not start from scratch. We applied the same
ladder.

The important shift was this: instead of relying on memory, chat history, or
individual intuition, we wrote the debugging method down in a form the AI agent
could load and follow.

## What the Skill Contains

The `opendss-device-qa` skill is not a long narrative. It is a compact operating
procedure. It tells Codex how to approach an OpenDSS versus InterPSS mismatch
without being re-taught the whole context each time.

The skill layout has several parts.

### 1. A Principle

The first section defines the core idea:

Treat a feeder mismatch as a localization problem, not a single pass/fail
number.

That one sentence matters. It prevents the debugging session from starting with
random code edits. A max voltage error is only a symptom. The skill tells the
agent to decompose the symptom into topology, path drops, device matrices,
currents, injections, controls, and finally mini-cases.

### 2. A QA Ladder

The QA ladder is the heart of the skill. It gives Codex an ordered checklist:

- Align the reference case.
- Establish the global signal.
- Separate convergence artifacts from modeling artifacts.
- Localize by topology and source path.
- Compare device physics before solver logic.
- Check KCL and KVL.
- Audit injections and controls.
- Stress parser assumptions with sensitivity toggles.
- Reduce to a mini-case.
- Capture durable evidence.

This ordering is important. Without it, it is easy to jump straight from "the
voltage differs" to "change the solver." The skill pushes the investigation
toward device semantics first: Yprim, tap ratio, phase mapping, load model,
capacitor state, source impedance, linecode units.

### 3. Known Compatibility Traps

The skill also records specific lessons we learned the hard way:

- Same-line `cmatrix` and `units` parsing can silently scale service impedance
  incorrectly.
- Repeated `Neutral=<n> Kron=yes` declarations matter.
- Missing OpenDSS load `kW` does not always mean zero load.
- Transformer `%rs=(...)` must be propagated into series resistance.
- If load P/Q already matches DSS-Python, look next at service linecode units,
  Kron reduction, and branch Yprim.

These notes are small, but they save real time. They turn a past debugging
session into a future starting point.

### 4. Decision Gates

The skill includes decision rules:

- Do deeper device QA when feeder residuals have structure by depth, phase, or
  region.
- Do not change solver logic until Yprim, KCL, KVL, injections, controls, and
  device states have been checked.
- Treat whole-feeder voltage comparison as a smoke test.
- Treat mini-cases and device checks as proof.
- If factorization fails at iteration zero, check islands, floating phases,
  near-zero impedance devices, and inactive phase nodes first.

These gates keep the agent from overfitting one symptom or making a broad change
too early.

### 5. Repo Hints and Commands

A useful skill must know where to start. The skill lists the important files and
commands:

- The main OpenDSS parser comparison test.
- QA utility classes.
- DSS-Python export scripts.
- Benchmark report locations.
- Focused Maven commands for comparison and diagnostic tests.

This gives Codex a map of the codebase. Instead of searching the whole
repository every time, it can begin at the relevant surfaces.

### 6. A Reporting Template

Finally, the skill includes a concise reporting template:

- Case
- Result
- Signal
- Device QA
- Root cause or next probe

That template matters because debugging output can easily become scattered. The
template turns exploratory work into a durable engineering artifact that another
person can read later.

## How the Skill Works in Practice

The practical loop looks like this:

1. The user asks Codex to investigate a mismatch.
2. Codex loads the `opendss-device-qa` skill.
3. The skill tells Codex which evidence to gather first.
4. Codex runs focused tests and diagnostics.
5. The investigation produces a suspected device or data issue.
6. Codex creates a mini-case or targeted comparison.
7. The fix is implemented and tested.
8. The lesson is added back into the skill or benchmark notes.

That last step is the compounding step. The AI does not just finish the task; it
helps update the method. The next debugging session starts with more context,
more traps documented, and better tests available.

In our work, this made a visible difference. IEEE 8500 helped us build the
initial QA ladder. Ckt24 then reused it and exposed a different signal: a large
near-source mismatch instead of a downstream accumulation. Ckt7 reused the same
process again and moved faster because the workflow, scripts, and comparison
tests already existed.

That is one of the most useful patterns for AI-assisted engineering: do not only
ask the AI to fix the current bug. Ask it to help preserve the debugging method
so that the next bug starts with better tools.

## How Codex Helped

Codex was useful not because it magically knew the answer. It was useful because
it could stay inside the engineering loop:

- Inspect the actual repo state.
- Read parser and solver code.
- Create targeted diagnostics.
- Run focused Maven tests.
- Generate and compare DSS-Python reference files.
- Add mini DSS cases.
- Update Markdown benchmark reports.
- Commit scoped changes.
- Convert repeated debugging steps into a reusable skill.

The interaction mattered. Human engineering judgment guided the hypotheses:
maybe the regulator tap ratio is line-line versus phase voltage; maybe the load
model changes below 0.9 pu; maybe the phase-C trend indicates a phase-specific
device; maybe Ckt24’s mismatch near depth zero is a transformer/source issue.
Codex then helped turn those hypotheses into executable checks.

The productive pattern was not "AI replaces domain expertise." It was more like
"domain expert plus tireless engineering assistant." The human kept asking the
right physics questions. Codex helped make the questions concrete in code,
tests, plots, and reports.

## What We Learned

Several lessons stand out.

First, convergence is not validation. A feeder can converge and still disagree
with the reference. Conversely, a failure to converge can be caused by a small
data-modeling issue such as an islanded phase, a floating transformer winding, or
a near-zero impedance branch.

Second, compare the whole device, not one scalar. For linecodes and transformers,
single numbers can be misleading. We often needed the full phase block, active
phase mapping, terminal convention, and Yprim comparison.

Third, controls are part of model compatibility, not a separate afterthought.
Regulator tap changes, capacitor control, control queues, delays, monitored
phase selection, and device states can all affect the solved result. Matching a
reference tool means treating those control semantics as part of the model.

Fourth, mini-cases are worth the time. They are the bridge between full-feeder
chaos and trustworthy device support.

Fifth, write down the method. Skills, TODO lists, benchmark reports, and
regression tests are how an AI-assisted debugging session becomes engineering
infrastructure.

## Why This Matters Beyond Distribution Studies

The reusable idea is not specific to OpenDSS or InterPSS.

Any complex power-system workflow that maps data across tools can use the same
pattern:

- Define a trusted reference.
- Align assumptions and control modes.
- Compare results at multiple levels: system, path, device, equation residual.
- Build mini-cases for uncertain semantics.
- Promote mini-cases into regression tests.
- Capture debugging experience as a reusable skill.

For transmission planning, the uncertain details might be transformer phase
shifts, switched shunts, generator reactive limits, contingency definitions, or
RAW-file defaults. For EMT studies, they might be initialization, controller
states, measurement filters, saturation models, or time-step conventions. For
protection studies, they might be relay curves, CT/PT ratios, topology state, or
fault impedance assumptions.

The names change. The method transfers.

## The Larger Lesson

The exciting part is not only that we fixed a set of parser bugs. It is that we
built a way to keep fixing the next ones faster.

The skill became a bridge between one engineer's hard-won experience and a
repeatable AI-assisted workflow. It captured where to look, what to compare,
what not to assume, which diagnostics matter, and when a mini-case is needed.

That is reusable far beyond this project. Any team that repeatedly maps complex
engineering data from one tool to another can do the same:

1. Solve the first hard case carefully.
2. Write down the debugging ladder.
3. Add the commands, files, traps, and reporting template.
4. Use the skill on the next case.
5. Feed new lessons back into the skill.

Over time, the team is not just accumulating fixes. It is accumulating
engineering judgment in an executable form.
