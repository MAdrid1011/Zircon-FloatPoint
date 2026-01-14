import chisel3._
import chisel3.util._

// IEEE 754 single-precision format constants
object IEEE754Constants {
    val SIGN_BIT = 31
    val EXP_HIGH = 30
    val EXP_LOW = 23
    val MANTISSA_HIGH = 22
    val MANTISSA_LOW = 0
    val MANTISSA_WIDTH = 23
    val EXP_WIDTH = 8
    val GRS_WIDTH = 3
    val FULL_MANTISSA_WIDTH = 27  // 24-bit mantissa + 3 GRS bits
    val EXTENDED_SHIFT_WIDTH = 32
    val EXTENDED_MANTISSA_WIDTH = 56
    val SHIFT_THRESHOLD_HIGH = 7
    val SHIFT_THRESHOLD_LOW = 5
}

object FPUtils {
    import IEEE754Constants._

    def floatBreakdown(x: UInt): (Bool, UInt, UInt) = {
        val sign = x(SIGN_BIT)
        val exponent = x(EXP_HIGH, EXP_LOW)
        val mantissa = x(MANTISSA_HIGH, MANTISSA_LOW)
        (sign, exponent, mantissa)
    }

    def Adder(src1: UInt, src2: UInt, cin: UInt, width: Int): (UInt, UInt) = {
        val result = src1 +& src2 +& cin(0)
        (result(width - 1, 0), result(width))
    }
}


class FAddIO extends Bundle {
    val src1 = Input(UInt(32.W))
    val src2 = Input(UInt(32.W))
    val op   = Input(Bool()) // 0: add, 1: sub
    val result = Output(UInt(32.W))
}


class FAddFarPathIO extends Bundle {
    val biggerSrc = Input(UInt(32.W))
    val smallerSrc = Input(UInt(32.W))
    val op = Input(Bool())
    val expDiff = Input(UInt(8.W))
    val result = Output(UInt(32.W))
}

class FAddFarPath extends Module {
    import FPUtils._
    import IEEE754Constants._

    val io = IO(new FAddFarPathIO())

    // Decompose floating-point operands into IEEE 754 fields
    val (biggerSign, biggerExponent, biggerMantissa) = floatBreakdown(io.biggerSrc)
    val (smallerSign, smallerExponent, smallerMantissa) = floatBreakdown(io.smallerSrc)

    // Determine effective operation (add or subtract based on signs)
    val farop = biggerSign ^ smallerSign ^ io.op

    // Add implicit leading 1 to mantissas
    val (biggerMantissaSB, smallerMantissaSB) = (Cat(1.U, biggerMantissa), Cat(1.U, smallerMantissa))
    val biggerMantissaFull = Cat(biggerMantissaSB, 0.U(GRS_WIDTH.W))

    // Align smaller mantissa by shifting right according to exponent difference
    val smallerMantissaExtended = Cat(smallerMantissaSB, 0.U(EXTENDED_SHIFT_WIDTH.W))
    val smallerMantissaExtendedShifted = Wire(UInt(EXTENDED_MANTISSA_WIDTH.W))
    smallerMantissaExtendedShifted := smallerMantissaExtended >> io.expDiff

    // Extract Guard, Round, and Sticky (GRS) bits for rounding
    val smallerMantissaExtendedShiftedLSB = smallerMantissaExtendedShifted(EXTENDED_MANTISSA_WIDTH - 1, EXTENDED_SHIFT_WIDTH)
    val smallerMantissaExtendedShiftedGR = smallerMantissaExtendedShifted(SIGN_BIT, EXP_HIGH)
    val isLargeShift = io.expDiff(SHIFT_THRESHOLD_HIGH, SHIFT_THRESHOLD_LOW).orR
    val smallerMantissaExtendedShiftedS = Mux(isLargeShift, smallerMantissa.orR, smallerMantissaExtendedShifted(EXP_HIGH - 1, 0).orR)
    val smallerMantissaFull = Cat(smallerMantissaExtendedShiftedLSB, smallerMantissaExtendedShiftedGR, smallerMantissaExtendedShiftedS)

    // Perform mantissa addition or subtraction
    val (sum, carry) = Adder(biggerMantissaFull, Mux(farop === 1.U, ~smallerMantissaFull, smallerMantissaFull), farop, FULL_MANTISSA_WIDTH)

    // First normalization stage: detect overflow or underflow
    val regularPlus1 = carry === !farop.asBool
    val regularMinus1 = sum(FULL_MANTISSA_WIDTH - 1) === 0.U && !regularPlus1
    val resultMantissaRegularized = Mux(
        regularMinus1, sum << 1,
        Mux(regularPlus1, Cat((farop.asBool && carry.asBool), sum)(FULL_MANTISSA_WIDTH, 2) ## sum(1, 0).orR, sum))

    // Extract mantissa and GRS bits after normalization
    val (resultMantissaSB, resultMantissaG, resultMantissaR, resultMantissaS) =
        (resultMantissaRegularized(FULL_MANTISSA_WIDTH - 1, GRS_WIDTH),
         resultMantissaRegularized(2),
         resultMantissaRegularized(1),
         resultMantissaRegularized(0))

    // Apply IEEE 754 round-to-nearest-even
    val roundPlus1 = resultMantissaG && (resultMantissaR || resultMantissaS || resultMantissaSB(0))
    val (resultMantissa, carry2) = Adder(resultMantissaSB, roundPlus1, 0.U, MANTISSA_WIDTH + 1)

    // Second normalization stage: handle rounding overflow
    val overflow = carry2 === 1.U
    val resultMantissaFinal = Cat(carry2, resultMantissa) >> overflow

    // Calculate result exponent with normalization adjustments
    val (resultExponent, _) = Adder(biggerExponent,
        Cat(Fill(EXP_WIDTH - 1, regularMinus1 && !overflow), regularPlus1 && overflow),
        regularPlus1 || (regularMinus1 ^ overflow),
        EXP_WIDTH
    )

    // Result sign matches the bigger operand
    val resultSign = biggerSign
    io.result := Cat(resultSign, resultExponent, resultMantissaFinal(MANTISSA_HIGH, MANTISSA_LOW))
}

// TODO: Complete FAdd module implementation
// This will include:
// - Near-path addition (for small exponent differences)
// - Integration of FAddFarPath for large exponent differences
// - Special value handling (NaN, Infinity, denormals, zero)