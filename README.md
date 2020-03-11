# Demo DSS Validator

Sample project which validates sample PDF file. Developed to show weirdly
different behaviour of validation (between multiple executions) in 5.6 version 
of DSS library.

## Execution

In the root directory of project simply run `./run.sh`. After that simple 
results can be found in `results.log` file.

Example results can be found below, as it can be seen, the results from multiple
runs are different, which is kind of unexpected during validation of the same
file with the same parameters.

```
Indication: INDETERMINATE
Subindication: OUT_OF_BOUNDS_NOT_REVOKED
------------------------------------------------------------
Indication: TOTAL_PASSED
Subindication: null
------------------------------------------------------------
Indication: INDETERMINATE
Subindication: OUT_OF_BOUNDS_NOT_REVOKED
------------------------------------------------------------
Indication: INDETERMINATE
Subindication: OUT_OF_BOUNDS_NOT_REVOKED
------------------------------------------------------------
Indication: TOTAL_PASSED
Subindication: null
------------------------------------------------------------
Indication: INDETERMINATE
Subindication: OUT_OF_BOUNDS_NOT_REVOKED
------------------------------------------------------------
Indication: TOTAL_PASSED
Subindication: null
------------------------------------------------------------
Indication: INDETERMINATE
Subindication: OUT_OF_BOUNDS_NOT_REVOKED
------------------------------------------------------------
Indication: INDETERMINATE
Subindication: OUT_OF_BOUNDS_NOT_REVOKED
------------------------------------------------------------
Indication: INDETERMINATE
Subindication: OUT_OF_BOUNDS_NOT_REVOKED
------------------------------------------------------------
```