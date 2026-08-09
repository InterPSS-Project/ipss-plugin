# Parser and build compatibility fixes (July 2026)

This note records the compatibility work needed to restore the `ipss-plugin`
and downstream `ipss-desktop` Maven reactors after the network-builder API
changes.

## Dynamic-stability and three-phase parsing

- The dynamic-stability parser now accepts `BaseDStabNetwork<?, ?>` and a
  caller-supplied network-object factory. This keeps the existing
  `DStabilityNetwork` API source-compatible while allowing three-phase callers
  to populate `DStabNetwork3Phase` directly.
- `PSSE3PhaseMultiFileLoader` centralizes creation and loading of a
  three-phase PSS/E network. Transmission/distribution tests use this loader
  instead of casting a conventional `DStabilityNetworkImpl`, eliminating the
  runtime `ClassCastException`.
- PSS/E `ACMTBLU1`/`USRLOD` records are parsed into the supported `LD1PAC`
  dynamic-load model. A zero PSS/E `BASKV` value is normalized to 1.0 kV so
  malformed input cannot create an invalid zero-voltage base.

## File-adapter corrections

- IEEE CDF branch parsing recognizes type-4 phase-shifting transformers and
  maps their angle-control limits and target range. Type-0 cross-voltage
  branches are represented as transformers so network validation remains
  consistent with the data.
- MATPOWER generator sequence tracking uses a map keyed by bus number rather
  than a fixed-size array. Large synthetic cases such as Texas 7k can therefore
  load bus identifiers above 99,999 without an array-bounds failure.
- JSON network comparisons use a small numeric tolerance for serialized
  floating-point values while continuing to compare topology and categorical
  fields exactly.

## Test-suite alignment

- Direct-parser tests now assert the network state produced by the current
  adapter instead of stale ODM/XML metadata.
- Legacy tests whose only input path was the intentionally removed `IEEE_ODM`
  XML adapter are class-disabled with an explanatory reason. Their direct-file
  parser replacements remain active.

## Verification

The final changes were verified with:

```text
cd ipss-plugin && mvn clean install
cd ipss-desktop && mvn clean install
```

Both complete multi-module reactors build, test, package, and install
successfully on Java 21.
