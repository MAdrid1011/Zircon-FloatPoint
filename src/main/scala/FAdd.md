# FAdd.scala Interface Documentation

## Overview

This file implements IEEE 754 single-precision floating-point addition hardware modules using Chisel3. It provides constants, utility functions, and the FAddFarPath module for far-path addition.

## External Interface

### Module: FAddFarPath

**Purpose**: Implements the far-path algorithm for IEEE 754 single-precision floating-point addition when the exponent difference is greater than 1.

**IO Bundle**: `FAddFarPathIO`

#### Inputs
- `biggerSrc: UInt(32.W)` - IEEE 754 single-precision float with larger exponent
- `smallerSrc: UInt(32.W)` - IEEE 754 single-precision float with smaller exponent
- `op: Bool()` - Operation selector (false = add, true = subtract)
- `expDiff: UInt(8.W)` - Exponent difference between biggerSrc and smallerSrc

#### Outputs
- `result: UInt(32.W)` - IEEE 754 single-precision floating-point result

#### Behavior
1. Decomposes input operands into sign, exponent, and mantissa fields
2. Aligns mantissas by shifting smaller operand right by expDiff positions
3. Computes Guard, Round, and Sticky (GRS) bits for correct rounding
4. Performs mantissa addition or effective subtraction based on signs
5. Applies two-stage normalization (handle leading zeros and rounding overflow)
6. Implements IEEE 754 round-to-nearest-even rounding mode
7. Adjusts exponent based on normalization shifts
8. Assembles result in IEEE 754 format

#### Constraints and Assumptions
- **Precondition**: biggerSrc must have exponent ≥ smallerSrc exponent
- **Precondition**: expDiff must be > 1 (far-path condition)
- **Does not handle**: Special values (NaN, Infinity, denormals, zero)
- **Does not handle**: Near-path cases (expDiff ≤ 1)

#### Error Conditions
- No explicit error signaling implemented
- Undefined behavior if preconditions are violated
- Special values will produce incorrect results

### Module: FAdd (Incomplete)

**Status**: Not yet implemented (see TODO comment in source)

**Planned functionality**:
- Near-path addition for small exponent differences (≤ 1)
- Integration with FAddFarPath for large exponent differences (> 1)
- Special value handling (NaN, Infinity, denormals, zero)
- Operand comparison and routing logic

## Internal Helpers

### Object: IEEE754Constants

**Purpose**: Defines IEEE 754 single-precision format bit positions and widths as named constants.

**Constants**:
- `SIGN_BIT = 31` - Sign bit position
- `EXP_HIGH = 30` - Exponent field high bit
- `EXP_LOW = 23` - Exponent field low bit
- `MANTISSA_HIGH = 22` - Mantissa field high bit
- `MANTISSA_LOW = 0` - Mantissa field low bit
- `MANTISSA_WIDTH = 23` - Mantissa field width in bits
- `EXP_WIDTH = 8` - Exponent field width in bits
- `GRS_WIDTH = 3` - Guard, Round, Sticky bits width
- `FULL_MANTISSA_WIDTH = 27` - Total mantissa width including GRS bits (24 + 3)
- `EXTENDED_SHIFT_WIDTH = 32` - Extension width for mantissa alignment
- `EXTENDED_MANTISSA_WIDTH = 56` - Total width for extended mantissa (24 + 32)
- `SHIFT_THRESHOLD_HIGH = 7` - High bit for large shift detection
- `SHIFT_THRESHOLD_LOW = 5` - Low bit for large shift detection

### Object: FPUtils

**Purpose**: Utility functions for floating-point operations.

#### Function: floatBreakdown

```scala
def floatBreakdown(x: UInt): (Bool, UInt, UInt)
```

**Purpose**: Decomposes IEEE 754 single-precision float into sign, exponent, and mantissa.

**Parameters**:
- `x: UInt` - 32-bit IEEE 754 single-precision float

**Returns**:
- `(sign: Bool, exponent: UInt(8.W), mantissa: UInt(23.W))` - Decomposed fields

**Algorithm**: Extracts bit fields using IEEE754Constants for bit positions.

#### Function: Adder

```scala
def Adder(src1: UInt, src2: UInt, cin: UInt, width: Int): (UInt, UInt)
```

**Purpose**: Performs unsigned addition with carry-in and produces result with carry-out.

**Parameters**:
- `src1: UInt` - First operand
- `src2: UInt` - Second operand
- `cin: UInt` - Carry-in (only bit 0 is used)
- `width: Int` - Width of result in bits

**Returns**:
- `(sum: UInt(width.W), carry: UInt(1.W))` - Truncated sum and carry-out

**Algorithm**: Uses Chisel3 widening addition operator `+&`, extracts lower bits as sum and MSB as carry.

### Bundle: FAddIO

**Purpose**: IO bundle for complete FAdd module (not yet implemented).

**Fields**:
- `src1: Input(UInt(32.W))` - First floating-point operand
- `src2: Input(UInt(32.W))` - Second floating-point operand
- `op: Input(Bool())` - Operation (false = add, true = subtract)
- `result: Output(UInt(32.W))` - Floating-point result

### Bundle: FAddFarPathIO

**Purpose**: IO bundle for FAddFarPath module.

**Fields**: See FAddFarPath module interface above.

## Implementation Notes

### Normalization Strategy

The implementation uses two-stage normalization:

1. **First stage** (after mantissa addition):
   - Detects overflow requiring right shift (`regularPlus1`)
   - Detects leading zero requiring left shift (`regularMinus1`)
   - Adjusts mantissa accordingly

2. **Second stage** (after rounding):
   - Handles overflow from rounding increment
   - Right shifts by 1 if rounding causes overflow

### Rounding Implementation

Implements IEEE 754 round-to-nearest-even (banker's rounding):
- Round up if G=1 and (R=1 OR S=1 OR LSB=1)
- Ensures exact halfway cases round to even mantissa LSB
- G (Guard), R (Round), S (Sticky) bits computed during alignment

### Large Shift Optimization

When expDiff indicates a very large shift (bits [7:5] non-zero):
- Uses optimized sticky bit computation
- Avoids shifting beyond available precision
- OR-reduces entire smaller mantissa to produce sticky bit

## Usage Example

```scala
// Instantiate FAddFarPath module
val farPath = Module(new FAddFarPath)

// Connect inputs (assuming biggerSrc has larger exponent)
farPath.io.biggerSrc := biggerOperand
farPath.io.smallerSrc := smallerOperand
farPath.io.op := false.B  // addition
farPath.io.expDiff := expDiffValue

// Read result
val result = farPath.io.result
```

## Testing

See `src/test/scala/FAddFarPathReferenceTest.scala` for comprehensive test coverage including:
- Basic addition and subtraction operations
- Rounding edge cases
- Normalization scenarios (left and right shifts)
- Large exponent differences
- Random test cases

## Future Work

- Complete FAdd module with near-path support
- Implement special value handling (NaN, Infinity, denormals)
- Add zero operand detection
- Integrate exception signaling (overflow, underflow, inexact)
- Support additional rounding modes beyond round-to-nearest-even
