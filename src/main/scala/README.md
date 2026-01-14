# Source Code - Main Hardware Modules

## Overview

This directory contains the main hardware implementation files for the Zircon FloatPoint project, written in Chisel3. These modules implement RISC-V floating-point arithmetic operations.

## Key Files

### FAdd.scala

**Purpose**: IEEE 754 single-precision floating-point addition hardware implementation.

**Components**:
- `IEEE754Constants` - Bit position and width constants for IEEE 754 format
- `FPUtils` - Utility functions for floating-point operations (breakdown, addition)
- `FAddFarPath` - Far-path addition module for large exponent differences (expDiff > 1)
- `FAdd` - (Planned) Complete addition module with near-path and special value handling

**Status**: FAddFarPath is complete and tested; FAdd module is incomplete.

**Documentation**: See `FAdd.md` for detailed interface documentation.

## Module Structure

### FAddFarPath Module

Implements the far-path algorithm for floating-point addition:
1. Mantissa alignment based on exponent difference
2. Guard/Round/Sticky bit computation for rounding
3. Two-stage normalization (pre-rounding and post-rounding)
4. IEEE 754 round-to-nearest-even implementation

**Inputs**: Two IEEE 754 floats (bigger and smaller by exponent), operation type, exponent difference

**Output**: IEEE 754 single-precision result

**Constraints**: Requires expDiff > 1 and correct operand ordering

## Integration Points

This module is intended to be integrated into:
- RISC-V floating-point execution units
- Custom floating-point accelerators
- Standalone arithmetic modules

## Dependencies

- **Chisel3**: Hardware construction language
- **chisel3.util**: Utility functions (Cat, Mux, Fill)

## Building

```bash
sbt compile
```

## Testing

Tests are located in `src/test/scala/`. Run tests with:

```bash
sbt test
```

## Future Additions

- FMul.scala - Floating-point multiplication
- FDiv.scala - Floating-point division
- FSqrt.scala - Floating-point square root
- FMA.scala - Fused multiply-add
