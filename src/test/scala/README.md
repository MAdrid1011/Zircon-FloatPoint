# Test Suite - Scala ChiselTest

## Overview

This directory contains the test suites for hardware modules, written using ChiselTest framework. Tests verify correctness of floating-point arithmetic implementations against IEEE 754 specifications.

## Test Files

### FAddFarPathReferenceTest.scala

**Purpose**: Comprehensive reference-based testing for the FAddFarPath module.

**Test Strategy**:
- Uses Scala's `Float` type as golden reference model
- Validates hardware implementation against software floating-point
- Generates VCD waveform output for debugging

**Coverage**:
- Basic addition and subtraction operations
- Rounding edge cases (round-to-nearest-even)
- Normalization scenarios (overflow and underflow)
- Large exponent differences
- Random test cases with various operand combinations
- Edge cases (maximum mantissa, minimum expDiff, etc.)

**Test Count**: 50+ test cases

### FAddClosePathTest.scala

**Purpose**: Comprehensive testing for the FAddClosePath module with leading zero prediction.

**Coverage**: Close-path addition, LZP validation, expDiff=0 and expDiff=1 cases, 10,000 random tests

**Test Count**: 10,000+ test cases

### FAddTest.scala

**Purpose**: Integration testing for the complete FAdd module with automatic path selection.

**Test Strategy**:
- Validates path selection logic (Far vs Close path based on expDiff)
- Tests sign correction for subtraction with swapped operands
- Comprehensive random testing across all path combinations

**Coverage**:
- Basic addition and subtraction operations
- Far path selection verification (10,000 test cases)
- Close path selection verification (1,000 test cases)
- Boundary testing around expDiff = 1
- Mixed sign operations
- 10,000+ comprehensive random test cases

**Test Count**: 22,000+ test cases total

## Test Organization

### Helper Functions

All test files include helper functions for IEEE 754 manipulation:
- `floatToBits(sign, exp, mantissa)` - Construct IEEE 754 from fields
- `extractFloat(bits)` - Decompose IEEE 754 into sign, exp, mantissa
- `bitsToFloat(bits)` - Convert BigInt to Scala Float
- `floatToBigIntBits(f)` - Convert Scala Float to BigInt bits

### Test Naming Convention

Test cases use descriptive names following the pattern:
```
it should "<operation> <scenario>" in { ... }
```

Examples:
- `"match Scala Float for basic addition cases"`
- `"handle rounding correctly with round-to-nearest-even"`
- `"preserve sign of bigger operand"`

## Running Tests

### Run all tests
```bash
sbt test
```

### Run specific test file
```bash
sbt "testOnly FAddFarPathReferenceTest"
```

### Run with verbose output
```bash
sbt "testOnly FAddFarPathReferenceTest -- -oD"
```

## VCD Waveform Generation

Tests generate VCD files in `test_run_dir/` for debugging:
- Enable in test: `test(new FAddFarPath).withAnnotations(vcdAnnotation) { ... }`
- View with GTKWave or similar waveform viewer
- Location: `test_run_dir/<TestName>/`

## Test Development Guidelines

### When Adding New Tests

1. **Add inline comments** documenting test purpose and expected behavior
2. **Use descriptive test names** that explain what is being tested
3. **Include edge cases** alongside typical cases
4. **Reference IEEE 754 spec** when testing rounding or special values
5. **Generate VCD output** for complex failing cases

### Test Documentation

Prefer inline comments for simple test suites:
```scala
// Test 1: Basic addition with expDiff=2 (2.0 + 0.5 = 2.5)
```

For complex test strategies, create companion `.md` file documenting:
- Overall test strategy
- Coverage goals
- Known limitations
- Reference to design documents

## Dependencies

- **ScalaTest**: Test framework
- **ChiselTest**: Hardware testing framework
- **ScalaCheck**: Property-based testing (optional for random tests)

## Test Results

All tests in this directory are expected to pass before merging to main branch. Use the pre-commit hook to enforce this:

```bash
# Install pre-commit hook
ln -s ../../scripts/pre-commit .git/hooks/pre-commit
```

## Future Test Additions

- Special value tests for FAdd (NaN, Infinity, denormals, zero)
- Exception flag validation tests
- Performance benchmarking tests
- Coverage analysis integration
