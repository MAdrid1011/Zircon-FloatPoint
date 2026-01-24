# FMul - Floating-Point Multiplication Module

## Overview

IEEE 754 single-precision floating-point multiplication hardware implementation using Booth-2 encoding and Wallace tree reduction for efficient mantissa multiplication.

## External Interface

### FMulIO Bundle

| Port | Direction | Width | Description |
|------|-----------|-------|-------------|
| `src1` | Input | 32 bits | First IEEE 754 single-precision operand |
| `src2` | Input | 32 bits | Second IEEE 754 single-precision operand |
| `result` | Output | 32 bits | IEEE 754 single-precision product |

### Module: FMul

**Purpose**: Complete floating-point multiplication with sign handling, exponent addition, mantissa multiplication, normalization, and rounding.

**Algorithm**:
1. Decompose operands into sign, exponent, and mantissa fields
2. Compute result sign as XOR of input signs
3. Add exponents and subtract bias (127)
4. Multiply mantissas using Booth-2 encoding and Wallace tree
5. Normalize result (shift if product >= 2.0)
6. Round to nearest even (IEEE 754 default rounding mode)
7. Handle overflow from rounding

**Constraints**:
- Does not handle special values (NaN, Infinity, denormals, zero)
- Assumes normalized inputs with implicit leading 1

**Usage**:
```scala
val fmul = Module(new FMul)
fmul.io.src1 := operand1
fmul.io.src2 := operand2
val product = fmul.io.result
```

## Internal Helpers

### FMulUtils Object

Utility functions specific to floating-point multiplication.

#### booth2(src1: UInt, src2: UInt): (UInt, UInt)

**Purpose**: Booth-2 radix-4 encoding for partial product generation.

**Inputs**:
- `src1`: Multiplicand (n bits)
- `src2`: 3-bit Booth encoding window

**Outputs**:
- `code`: Partial product (n bits)
- `add_1`: Correction bit for two's complement (1 bit)

**Encoding Table**:
| src2 | Operation | code | add_1 |
|------|-----------|------|-------|
| 000 | +0 | 0 | 0 |
| 001 | +M | src1 | 0 |
| 010 | +M | src1 | 0 |
| 011 | +2M | src1 << 1 | 0 |
| 100 | -2M | ~(src1 << 1) | 1 |
| 101 | -M | ~src1 | 1 |
| 110 | -M | ~src1 | 1 |
| 111 | -0 | 0 | 0 |

#### csa(src1: UInt, src2: UInt, cin: UInt): (UInt, UInt)

**Purpose**: Carry-save adder for Wallace tree reduction.

**Inputs**:
- `src1`, `src2`, `cin`: Three input operands (same width)

**Outputs**:
- `sum`: Sum bits (same width as inputs)
- `cout`: Carry bits (same width as inputs)

**Logic**:
```
sum = src1 XOR src2 XOR cin
cout = (src1 AND src2) OR (src1 AND cin) OR (src2 AND cin)
```

#### wallceTree13Cin10(src: UInt, cin: UInt): (UInt, UInt)

**Purpose**: 13-input Wallace tree with 10-bit carry-in for partial product reduction.

**Inputs**:
- `src`: 13 partial product bits (one bit from each of 13 partial products)
- `cin`: 10-bit carry-in from previous column

**Outputs**:
- `sum`: 1-bit sum
- `cout`: 11-bit carry-out to next column

**Reduction Stages**:
```
13 inputs → 9 → 6 → 4 → 3 → 2 outputs
```

**Note**: The 11th carry bit cannot be realized within the tree structure and is handled separately in the final addition stage.

## Algorithm Details

### Mantissa Multiplication

The 24-bit mantissas (with implicit leading 1) are multiplied using:

1. **Booth-2 Encoding**: Generates 13 partial products from the 24-bit multiplier
   - Each partial product is 48 bits wide
   - Reduces number of additions compared to simple shift-and-add

2. **Wallace Tree Reduction**: Reduces 13 partial products to 2 using CSA
   - 48 parallel Wallace trees (one per bit position)
   - Each tree takes 13 inputs plus carry-in from previous position
   - Produces sum and carry vectors for final addition

3. **Final Addition**: Adds the two vectors from Wallace tree
   - Includes correction for Booth encoding negative values
   - Produces 48-bit product

### Exponent Calculation

```
result_exponent = exp1 + exp2 - bias + normalization_adjustment
```

Where:
- `bias = 127` for single precision
- `normalization_adjustment = +1` if product >= 2.0, `0` otherwise

### Rounding

Implements IEEE 754 round-to-nearest-even:
- Guard bit (G): First bit beyond mantissa
- Round bit (R): Second bit beyond mantissa  
- Sticky bit (S): OR of all remaining bits

Round up when: `G AND (R OR S OR LSB)`

## Dependencies

- `FPUtils.floatBreakdown` - IEEE 754 field extraction (from FAdd.scala)
- `FPUtils.Adder` - Parameterized adder with carry (from FAdd.scala)

## Limitations

- No support for special values (NaN, Infinity, denormals)
- No support for zero inputs
- No exception flags (overflow, underflow, inexact)
- Single-cycle combinational implementation (no pipelining)

## Related Files

- `FMul.scala` - Implementation source
- `FMulTest.scala` - Test suite
- `FAdd.scala` - Shared utilities (FPUtils)
