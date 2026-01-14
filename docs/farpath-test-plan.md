# FarPath ChiselTest Implementation and Debug Plan

## Problem Statement

The FAddFarPath module in `src/main/scala/FAdd.scala` implements the far path for RISC-V single-precision floating-point addition. It currently handles:
1. Cases where operand A is larger than operand B
2. Cases where the exponent difference is greater than 1 (>1)

This module requires comprehensive ChiselTest coverage to ensure correctness and facilitate debugging of the design and implementation.

## Proposed Solution

### Test Coverage Strategy

The test suite (`src/test/scala/FAddFarPathTest.scala`) covers the following scenarios:

#### 1. Basic Operations
- **Basic addition with expDiff=2**: Tests fundamental addition with minimal exponent difference
- **Addition with large exponent difference**: Tests alignment and sticky bit handling
- **Subtraction with same sign**: Tests effective addition through subtraction operation
- **Effective subtraction**: Tests different signs with addition operation

#### 2. Rounding and Normalization
- **Round to nearest, ties to even**: Validates IEEE 754 rounding mode
- **Left shift normalization**: Tests regularMinus1 path when result needs normalization
- **Right shift normalization**: Tests regularPlus1 path when overflow occurs
- **Sticky bit computation**: Ensures proper handling of bits shifted out during alignment

#### 3. Edge Cases
- **Very large exponent differences (>32)**: Tests when smaller operand is mostly lost
- **Sign preservation**: Validates that result sign matches bigger operand
- **Minimum exponent difference**: Tests boundary condition (expDiff=2)
- **All mantissa bits set**: Tests maximum mantissa values in both operands
- **Subtraction resulting in smaller magnitude**: Tests cancellation scenarios

#### 4. Implementation Details Tested
- **Mantissa alignment** (lines 44-52 in FAdd.scala)
- **GRS (Guard, Round, Sticky) bit handling** (lines 49-52)
- **Two-stage normalization** (lines 56-58, 64-65)
- **Exponent adjustment** (lines 67-71)
- **Rounding logic** (lines 61-62)

### Test Implementation

The test file includes:
- Helper functions for IEEE 754 bit manipulation
- 13 comprehensive test cases covering all critical paths
- Assertions for sign, exponent, and mantissa correctness
- Edge case validation

### Debug Strategy

1. **Run initial tests**: Execute the test suite to identify failures
2. **Analyze failures**: Examine which test cases fail and why
3. **Trace signal values**: Use ChiselTest peek/poke to inspect intermediate values
4. **Fix implementation issues**: Correct bugs in FAdd.scala based on test results
5. **Verify fixes**: Re-run tests to ensure all cases pass
6. **Add additional tests**: If new edge cases are discovered during debugging

### Files Modified/Created

- **Created**: `src/test/scala/FAddFarPathTest.scala` - Comprehensive test suite
- **To be debugged**: `src/main/scala/FAdd.scala` - FarPath implementation

### Known Limitations

The current FarPath implementation has constraints:
- Requires `biggerSrc` to have larger exponent than `smallerSrc`
- Requires exponent difference > 1
- Does not handle special values (NaN, Infinity, denormals)
- Does not handle zero operands

These limitations should be documented and tested separately when implementing the complete FAdd module with near path support.

## Test Strategy

### Execution
```bash
sbt test
```

### Validation Criteria
- All 13 test cases must pass
- No assertion failures
- Correct handling of:
  - Sign propagation
  - Exponent calculation
  - Mantissa alignment and rounding
  - Normalization in both directions

### Debug Process
1. Run tests and collect failure information
2. For each failure:
   - Identify the specific operation (addition/subtraction)
   - Check intermediate values (mantissa alignment, GRS bits, normalization flags)
   - Verify exponent calculation logic
   - Validate rounding behavior
3. Fix implementation bugs
4. Re-run tests until all pass

## Implementation Notes

- The test suite uses ScalaTest with ChiselTest framework
- IEEE 754 single-precision format is used throughout
- Test cases are designed to exercise all code paths in FAddFarPath
- Helper functions facilitate test case creation and result validation
