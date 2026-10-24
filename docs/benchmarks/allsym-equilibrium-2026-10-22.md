# Supplied ALLSYM equilibrium case

The unchanged `ALLSYM.raw` and `GEN_ESST4B_IEEEG1_IEEEST.dyr` pair now has a
separate ten-second no-disturbance regression test, `AllsymEquilibriumTest`.
It supplements the original six cases. Flat start here means stationary
dynamic states at the solved operating point, not forcing all buses to unit
voltage and zero angle.

The current-source run passes with pure Java KLU, a 1/240-second integration
step, 1e-5 pu network voltage-correction tolerance, and no negative-time startup
steps. Power flow uses the loaded voltages, generator-first shunt coordination,
fixed transformer/area controls, and a 1e-3 pu mismatch tolerance. It converged
in 10 iterations to a maximum mismatch of 4.3560e-8 pu. Loads use constant-current
active power and constant-impedance reactive power.

| Quantity | Largest absolute change over ten seconds | Gate |
| --- | ---: | ---: |
| Bus voltage | 2.998e-15 pu | <1e-6 pu |
| Bus voltage angle | 1.279e-13 degrees | <0.001 degrees |
| Machine speed | 0 pu | <1e-7 pu |
| Rotor angle | 0 degrees | <0.001 degrees |
| Machine terminal P | 3.064e-14 pu | <1e-6 pu |
| Machine terminal Q | 3.197e-14 pu | <1e-6 pu |

All 5,728 channels are checked at every integration endpoint: 2,401 samples
including initialization, covering 2,000 active buses and 432 active machines.
The retained trace contains the maximum drift in each quantity at every endpoint;
this is an equilibrium gate, not a full-network trajectory export or timing benchmark.
Absolute gates avoid dividing by the zero range of a flat trajectory.

The state audit covers 4,790 machine/controller derivatives at initialization,
after the first step, and at ten seconds. Maximum absolute derivatives are
7.4385e-14, 5.7732e-14, and 2.2204e-14 respectively, with no coverage gaps in
the active imported stack. The audit now includes both states of the IEEEST
second-order blocks through their pure slope accessors. Seven audit tests and
the full-case test passed (eight tests, zero skips or failures).

## Input coverage and explicit preparation assumption

| Model | Supplied records | Active attached models |
| --- | ---: | ---: |
| GENSAL | 123 | 118 |
| GENROU | 410 | 314 |
| ESST4B | 276 | 210 |
| IEEEG1 | 18 | 1 |
| IEEEST | 432 | 210 |

There are 156 IEEEST records whose machines have no exciter in the supplied
pair; 121 of those machines are active. The strict importer rejects those
records. This fixture explicitly assumes fixed excitation for a machine with
no exciter, and treats its unconnected stabilizer output as unused. It retains
the exact list and original import report, requires all 1,259 records to be
accounted for, and rejects every other import failure. No exciter or governor
is invented, no generator is netted, and neither supplied file is edited.
This assumption awaits confirmation if a companion exciter file was intended.
The production importer is unchanged.

An isolated attempt to attach those stabilizers without changing the runtime
initialization was rejected: the runtime does not initialize a stabilizer on
a machine without an exciter. That experiment was not promoted.

## Reproduction and evidence

Install the private input pair in
`ipss.test.plugin.core/testData/private/allsym-equilibrium-20261022/` with the
names above. The test pins both SHA-256 values and skips only when the private
fixture directory is absent. Details and hashes are in
[the case registry](checkpoints/allsym-equilibrium-2026-10-22.json).

After building/installing the matching current core dependency, run:

```powershell
python tools/run_bounded.py --log target/allsym-equilibrium-tests.log -- mvn.cmd -pl ipss.test.plugin.core -am test '-Dtest=AllsymEquilibriumTest,Texas2kInitializationAuditTest' '-DargLine=-Xmx2g'
```

The local Maven core JAR was older than the source API and prevented the
ordinary reactor build. Validation therefore compiled all 1,835 current core
and 884 plugin Java sources into isolated output directories, then ran the
new test and audit tests through the JUnit launcher. The saved source/build
hashes identify that executable; no core JAR replacement was needed.

This establishes undisturbed equilibrium for this supplied pair under the
explicit unused-stabilizer policy. It does not establish disturbance accuracy
against an independent trajectory, qualify the original six cases, or measure
warmed performance.
