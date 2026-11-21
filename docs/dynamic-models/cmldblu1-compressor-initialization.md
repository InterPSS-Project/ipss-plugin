# Composite compressor loading and operating-point preparation

Status: independently checked legacy compressor initialization and shared
modified-Euler/staggered execution. Complete model qualification remains open;
the catalog remains `LOADABLE`.

## One loading-factor parameter

`LD1PACImpl` now uses its inherited loading-factor storage for both the public
accessors and numerical equations. Previously an additional field stayed at
0.85 while the inherited setter changed a different field. Configured loading
therefore failed to reach the calculated rating, thermal initialization and
voltage adjustments. The constructor initializes the shared field to 0.85.

The standalone regression checks fail against the frozen old implementation.
They check physical rating, temperature, voltage thresholds, equilibrium and
accepted updates under both integration methods. Reinitialization starts from
the configured thresholds, so the loading adjustment does not compound.

## Explicit characteristic policies

`LD1PACImpl.CharacteristicPolicy` separates three preparation conventions:

- `STANDALONE` retains the characteristic anchored to the supplied operating
  point and the multiplicative loading adjustment.
- `LEGACY_COMPOSITE`, selected by `Cmldblu1Model`, prepares nominal-voltage
  coefficients, an additive voltage adjustment and an initial reactive-demand
  matching admittance.
- `ZONE_COMPOSITE`, selected by `Cmldznu2Model`, retains its operating-point
  coefficients and unadjusted voltage thresholds. Independent zero/nonzero
  adjustment controls check this distinction while preserving the input data.

The legacy policy uses the configured, unadjusted breakdown voltage `Vb0`:

```text
P0 = 1 - Kp1 * (1 - Vb0)^Np1
Q0 = tan(acos(CompPF)) - Kq1 * (1 - Vb0)^Nq1
Vb = Vb0 + LFadj * (CompLF - 1)
Vs = Vs0 + LFadj * (CompLF - 1)
```

Its running active characteristic includes `CompLF`; its reactive
characteristic has no corresponding loading multiplier. The applicable
high/low-voltage polynomial branch supplies the initial active power used to
calculate motor MVA. Initial temperature uses the electrical motor current
before parallel reactive compensation. Both compressor groups start from that
same temperature.

The difference between electrical motor Q and its allocated terminal Q becomes
a physical parallel admittance. The total stamp contains the electrical Norton
admittance and this correction. The injected Norton current uses the electrical
admittance only, preserving the physical terminal demand and the motor's heating
current. Distribution compensation remains a separate physical element.

These policies prepare coefficients and storage for one numerical
implementation. They introduce no second state vector or independent derivative
kernel. The compressor still has four continuous coordinates and reusable
rate/current buffers; accepted protection histories remain separate.
Policy selection locks when initialization completes, including before the
first Norton stamp is read. A rejected selection leaves the existing states
unchanged.

## Shared distribution geometry and reactive accounting

The two composites share the analytic transformer/tap/feeder preparation in
`Cmldznu2Model`. The legacy policy retains its allocated reactive demand and
near/far compensation split. The zone policy includes the high-side fixed-tap
factor in transformer reactance and locates calculated compensation at the
feeder's far end. Zero/nonzero feeder-split controls independently check the
zone convention; the physical substation shunt remains at the low-side bus.

The internal bus retains gross demand owned by its components. The explicitly
stamped capacitors supply the difference from net feeder Q. This prevents the
dynamic-network builder from adding a second residual static compensation.
The fixed-point current refresh continues to use the current network voltage.

## Independent checks and remaining limits

Cold-paired synthetic controls exercise loading, loading adjustment and
compensation location. Complete allocated states, diagnostics, startup samples
and repeated run-boundary timestamps are retained with exact input/output
hashes. Additional cold pairs regenerate the public small-network zone fault.
The fault test uses independently captured initial electrical coordinates;
its existing trajectory tolerances remain unchanged.

Across the complete retained half-second legacy boundary comparisons, the worst
voltage NRMSE is now about `6.24e-5` and the worst rotor-angle RMSE is about
`0.00016442` degrees. The stronger repeated-stall fault remains an unqualified
coupled diagnostic with a voltage peak error near `0.01038` pu; these results
have separate acceptance scopes.

`Cmldblu1CompressorInitializationTest` checks the legacy rating, thermal state,
polynomial coefficients, adjusted thresholds and reactive matching under both
integration methods. It also retains zone initialization errors as explicit
diagnostics: the zone reactive polynomial and thermal observations still need
further qualification. Successful threshold and external-trajectory gates do
not establish complete zone internal-state parity.

`LD1PACParameterBindingTest` checks the shared parameter contract and fresh
initialization. Fresh initialization is distinct from warm restoration; full
compressor serialization, active protection/reclosure, upstream measurement
semantics and production-step/long-duration qualification remain open. The
[repeated compressor histories](cmldblu1-compressor-lifecycle.md) retain their
independent action-time gates and complete unqualified coupled diagnostics.

The exact old, intermediate and maintained executables, rejected attempts,
runnable probes, source captures and validation reports remain in the private
verification-artifact chain. No complete-run speedup is claimed from these
initialization corrections.

Validation: 186 focused checks pass across the reactor, including the
parameter-binding, independent initialization, shared-buffer and trajectory
gates. The core module passes 458 checks with four existing skips, and all
15 three-phase motor checks pass. The registered suite executes 2,928 checks
with the same four baseline failures, zero errors and 13 skips. This retains
the incomplete model-qualification status and does not establish a completely
green system gate.
