### Sparse Equation Implementation Notes

The sparse equation [A] X = B solution process has three main steps:

* Factorization : The [A] matrix row/column number is re-arrange to minimize the non-zero fill-ins.

* LU Decomposition: LU decomposition of [A] matrix to increase the efficiency

* Equation Solution: With a given B, solve the eqn for X.

##### Apache Common Math Sample Implementation

The common math full matrix library is used in the implementation, therefore the above three steps are not fully illustrated.