# GEWGD1 source contract and implementation gate

GEWGD1 remains unsupported. No exact runtime, attachment or trajectory acceptance
is claimed. The installed model documentation describes GEWTGDU1, a different
identifier, and does not establish an equivalent legacy GEWGD1 implementation.

## Evidence boundary

The legacy model belongs to the vendor wind suite. Its source record uses a
user-model type distinct from the newer GEWTGDU1 model. Matching allocation sizes
alone do not establish identical equations, parameter meanings, wind-speed units,
initialization, event timing or host bindings.

The [conversion vendor release notes](https://www.powerworld.com/patch/october-21-2015)
explicitly describe converting the legacy suite into generic WT3 models. That
conversion is useful import evidence, but cannot qualify an exact GEWGD1 runtime.
The [independent importer support document](https://www.spp.org/documents/67445/tsat%20support%20for%20data%20in%20psse%20format.pdf)
also labels the legacy suite as internally converted. Neither source provides
an exact GEWGD1 equation and lifecycle contract.

## Converted-suite auxiliary sensitivity

A synthetic conversion discriminator retained the same generator and companion
records while omitting only GEWGD1. A second pair changed only GEWGD1's first
entered constant to `0.04` or omitted that changed record. The first constant's
legacy meaning is not established, so this is a slot perturbation, not an
assumed gust-start setting. The complete converted voltage, P/Q and available
state export was byte-identical across the baseline, omission, perturbed and
perturbed-omission controls. A cold repeat of the perturbed control reproduced
both the staged input and exported output exactly. The dynamic-model readbacks
after import and initialization were also identical for each omission pair.

This establishes insensitivity of the installed **converted** runtime to that
record and slot in the exercised short profile. It does not establish the
behavior of the original GEWGD1 executable, nor whether another slot, event
window or host profile would affect the conversion. The repeatable private
inputs, model readbacks, full exports and bounded logs are in
`verification-artifacts/legacy-ge-aux-sensitivity-20261105/evidence.zip`,
SHA-256 `47b874c89ecf72849974694471c2a5e7f56acd132d3ef441ee0cf11fc49ef430`.
The public `tools/legacy_ge_powerworld_import_probe.py` adds controlled
auxiliary omission and first-slot perturbation without publishing source
records. The legacy implementation gate below remains unchanged.

## Required implementation work

1. Obtain the original model data sheet and a runnable matching vendor library.
   Identify its version and hashes in private evidence, without publishing source
   case identities, model-instance counts or private parameter records.
2. Establish the six constant meanings, internal integer meaning, four observable
   outputs and exact wind gust/ramp equations, including zero-duration behavior.
3. Establish the generator/aerodynamic host identity and wind-speed unit/base
   boundary. Do not attach the legacy auxiliary to GEWTGCU1 merely because the
   newer auxiliary has a similar role.
4. Implement strict record validation and explicit host binding. Reuse the
   existing numerical gust/ramp kernel only after equivalence is demonstrated.
5. For both modified Euler and staggered integration, use a common accepted
   simulation clock and pure endpoint signal evaluation; verify predictor,
   corrector, repeated observation, event boundaries, rollback and reinitialization
   against the established source contract before choosing stage semantics.
6. Capture synthetic SMIB equilibrium, nonzero gust, positive/negative ramp,
   overlapping signals and boundary-time profiles. Compare every published output
   with the exact independent implementation at the production interval and a
   refined interval, retaining event-side duplicates and original hashes.

No substitute model or unverified alias should silently make a GEWGD1 record
appear supported. Full implementation and independent SMIB qualification remain
pending the exact source and host contracts.
