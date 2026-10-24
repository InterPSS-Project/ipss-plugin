# ReliCapGrid shared grid definitions

Source: ENTSO-E's [ReliCapGrid](https://github.com/entsoe/relicapgrid), commit
`5fee55c758f27fc37412f95cd3f4a86bdf6a4645`, file
[`Instance/commonData/Grid/cimxml/Grid_CommonData_CGM-CD.xml`](https://github.com/entsoe/relicapgrid/blob/5fee55c758f27fc37412f95cd3f4a86bdf6a4645/Instance/commonData/Grid/cimxml/Grid_CommonData_CGM-CD.xml).

This unmodified Common Data file supplies the base-voltage resources referenced
by the Svedala equipment and topology profiles. Its model identifier is
`1b65ada7-39ca-42b8-a278-39c457ed82c3`, which those profiles declare as a
dependency. Combine it with the existing grid profiles and relevant border
files, as described by the upstream package-assembly instructions. The existing
four Svedala profile files are preserved.

Copyright ENTSO-E. Licensed under CC BY-SA 4.0; see [LICENSE.md](LICENSE.md),
copied from the same upstream revision. No model values were changed.
