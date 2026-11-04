# Standard-converter resistance and AC power

Core commit `8bc4f8e20`, following `f5d6ec07b`, adds the previously omitted
commutating-transformer resistance to the standard LCC equations. A shared
`LccConverterElectricalTerms` helper supplies the same coefficients to the static
converter and coupled network injection model. Capacitor-commutated lines remain
outside this correction.

Let `k = 3*N*X/pi` and `r = 2*N*R`, with resistance and reactance in ohms per
bridge. In positive DC-voltage magnitude coordinates:

- Rectifier: `Vr = V0r*cos(alpha) - (kr + rr)*I`.
- Inverter: `Vi = V0i*cos(gamma) - (ki - ri)*I`.
- Rectifier AC power into the converter: `Pr = Vr*I + rr*I²`.
- Inverter AC power into the converter: `Pi = -Vi*I + ri*I²`.

The inverter sign follows conversion from signed bridge voltage using
`alpha = pi - gamma - overlap`. Its positive voltage magnitude reverses the
resistive voltage-drop sign; resistor dissipation remains positive at both ports.
The primary document's visually identical negative terms are retained privately
with this coordinate derivation and independent terminal measurements. Applying
that rendering directly to unsigned inverter voltage would produce the wrong
current response.

Reactive power uses the AC active-power magnitude and the existing overlap/power-
factor equation. The coupled solver includes the corresponding loss derivatives
in every voltage and control partial. Power orders continue to refer to DC power
at their selected metering end, rather than AC power plus transformer loss.

## Validation and remaining comparison failures

- The full core suite passes: **3,803 tests, zero failures/errors, 16 skips**.
  Compared with the previous correction, exactly two new tests were added and
  skip identities are unchanged. Tests cover both resistance locations, all three
  active control combinations, both DC-metering ends, power balance, committed
  injections, and centered finite-difference voltage/control derivatives.
- Eight independent paired profiles vary zero/rectifier/inverter/both-end
  resistance at both metering ends, with unchanged zero capacitor reactance and
  locked taps. Direct converter AC P/Q avoids auxiliary-network attribution.
- The prior implementation fails six of eight strict profile comparisons; the
  correction fails two. Both zero-resistance profiles retain **byte-identical**
  Java numerical outputs, enforced by the regression test.
- The two remaining failures are retained. Their reference DC power differs from
  its own order by **0.005389480 MW**. The combined binary32 readback uncertainty
  is at most **0.000211517 MW**, leaving at least **0.005177963 MW** unexplained by
  readback rounding. An extra unchanged solve and a new cold pair with tighter
  AC mismatch tolerance preserve the same converter result. The implementation
  satisfies the DC order and resistor power balance; it is not adjusted to
  reproduce that residual. No comparison threshold was relaxed.
- The four previous inverter-voltage-control reference gates still pass. The
  original full-sample assertions remain unchanged with the same six failures;
  capacitor-commutated modeling remains open.

The uniquely versioned candidate artifact is `1.4.8-lcc-resistance-final`; shared
release artifacts and default dependencies were not changed. This qualification
supports the equation correction, not a claim that every reference gate passes.

Private evidence directory: `testData/private/lcc-resistance-20261104`.

| Artifact | SHA-256 |
| --- | --- |
| `reference.zip` | `b190cd9481cec14edc749dea3caccab53cbfdb5883dc0b26c6977361695fddc7` |
| `zero-resistance-baseline.zip` | `9208f6a119b4311d470ac0443eeae569fcd8477ec5e9fe0dd0c6517886842dea` |
| `reference-quality.zip` | `b9c4514e840208c7968721d28f7d5abbc5e5d940ae95d128bb53517fb2a439f8` |
| `validation.zip` | `fbb040c5e770170f2db5ff365c359c9879695c2f99fedaf7a112ef607fd51c96` |

Prepared inputs, unmodified original captures, paired outputs, quality controls,
source provenance, artifact hashes, complete core reports and failed comparison
logs are retained. `LccResistanceReferenceTest` keeps the two strict failures
visible. The prepared-input capture utility records any explicit extra solve or
mismatch-tolerance change separately.
