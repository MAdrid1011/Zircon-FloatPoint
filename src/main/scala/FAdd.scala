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


class FAddPathIO extends Bundle {
    val biggerSrc = Input(UInt(32.W))
    val smallerSrc = Input(UInt(32.W))
    val op = Input(Bool())
    val expDiff = Input(UInt(8.W))
    val result = Output(UInt(32.W))
}

class FAddFarPath extends Module {
    import FPUtils._
    import IEEE754Constants._

    val io = IO(new FAddPathIO())

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


class FAddClosePath extends Module {
    import FPUtils._
    import IEEE754Constants._

    val io = IO(new FAddPathIO())

    // Decompose floating-point operands into IEEE 754 fields
    val (biggerSign, biggerExponent, biggerMantissa) = floatBreakdown(io.biggerSrc)
    val (smallerSign, smallerExponent, smallerMantissa) = floatBreakdown(io.smallerSrc)

    // Determine effective operation (add or subtract based on signs)
    val closeop = biggerSign ^ smallerSign ^ io.op

    // Add implicit leading 1 to mantissas
    val (biggerMantissaSB, smallerMantissaSB) = (Cat(1.U, biggerMantissa), Cat(1.U, smallerMantissa))
    val biggerMantissaFull = Cat(biggerMantissaSB, 0.U(1.W))

    // Align smaller mantissa by shifting right according to exponent difference
    val smallerMantissaExtended = Cat(smallerMantissaSB, 0.U(1.W))
    val smallerMantissaFull = Mux(io.expDiff(0), smallerMantissaExtended >> 1, smallerMantissaExtended)
    // leading zero predictor
    def leadingZeroPredictor(src1: UInt, src2: UInt): (UInt, UInt, UInt) = {
        val n = src1.getWidth
        assert(n == src2.getWidth, "src1 and src2 must have the same width")
        val (src1Pad, src2Pad) = (Cat(src1, 0.U(1.W)), Cat(src2, 0.U(1.W)))
        val xor = src1Pad ^ src2Pad
        val z = src1Pad | ~src2Pad
        val f = VecInit.tabulate(n){i =>
            xor(i+1) && z(i)    
        }.asUInt
        val lz = PriorityEncoder(Reverse(f))
        val (lzPlus1, _) = Adder(lz, 1.U, 0.U, lz.getWidth)
        val (lzMinus1, _) = Adder(lz, ~(1.U), 1.U, lz.getWidth)
        (lz, lzPlus1, lzMinus1)
    }
    val (lzPredictBigger, lzPlus1Bigger, lzMinus1Bigger) = leadingZeroPredictor(biggerMantissaFull, smallerMantissaFull)
    val (lzPredictSmaller, lzPlus1Smaller, lzMinus1Smaller) = leadingZeroPredictor(smallerMantissaFull, biggerMantissaFull)
    // perform addition or subtraction for mantissa
    val (sum, carry) = Adder(biggerMantissaFull, Mux(closeop === 1.U, ~smallerMantissaFull, smallerMantissaFull), closeop, 25)
    // select appropriate LZP based on carry (indicates which operand was actually larger)
    val lzPredict = Mux(carry === 1.U, lzPredictBigger, lzPredictSmaller)
    val lzPlus1 = Mux(carry === 1.U, lzPlus1Bigger, lzPlus1Smaller)
    val lzMinus1 = Mux(carry === 1.U, lzMinus1Bigger, lzMinus1Smaller)
    // if the expDiff == 0, and closeop is minus and the carry is zero, then we need to get the absolute value of the sum
    // whatever the expDiff is, if the closeop is add, we need to regularize the result mantissa
    val subFix = !io.expDiff(0) && closeop === 1.U
    val resultMantissaRegularized = Mux(closeop === 0.U && carry === 1.U, Cat(carry, sum), sum ## 0.U(1.W))
    val roundPlus1 = (closeop === 0.U || sum(24) === 1.U) && resultMantissaRegularized(1) && (resultMantissaRegularized(2) || resultMantissaRegularized(0))

    // round off or get the absolute value of the sum
    // For subFix (subtraction with expDiff=0): compute absolute value
    // For normal case: perform rounding on regularized mantissa
    val adderSrc1 = Mux(subFix, 0.U, resultMantissaRegularized(25, 2))
    val adderSrc2 = Mux(subFix, Mux(carry === 0.U, ~(sum(24, 1)), sum(24, 1)), 0.U)
    val adderCarryIn = Mux(subFix, Mux(carry === 0.U, 1.U, 0.U), roundPlus1)
    val (resultMantissaRoundOffTemp, resultMantissaRoundOffCarry) = Adder(adderSrc1, adderSrc2, adderCarryIn, 24)
    val resultMantissaRoundOff = resultMantissaRoundOffTemp ## (closeop & resultMantissaRegularized(1))
    val overflow = resultMantissaRoundOffCarry === 1.U && !subFix

    // fix prediction enable
    def fixPredictionEn(src: UInt, prediction: UInt): Bool = {
        val srcShifted = src << prediction
        !srcShifted(24)
    }
    val fixPredictionEnable = fixPredictionEn(resultMantissaRoundOff, lzPredict)
    val lzOffset = Mux(fixPredictionEnable, lzPlus1, lzPredict)
    // compute final mantissa based on operation type
    val mantissaWithCarry = Cat(resultMantissaRoundOffCarry, resultMantissaRoundOff)
    val resultMantissa = Mux(closeop === 1.U,
        // subtraction: normalize with LZP, then adjust for overflow
        {
            val carryBit = Mux(io.expDiff(0), resultMantissaRoundOffCarry, 0.U(1.W))
            val mantissaExtended = Cat(carryBit, resultMantissaRoundOff)
            (mantissaExtended << lzOffset) >> overflow
        },
        // addition: just adjust for overflow
        mantissaWithCarry >> overflow
    )
    // compute result exponent with adjustments for normalization and overflow
    val exponentAdjustment = Mux(closeop === 0.U,
        // addition: increment exponent if overflow occurred
        carry & overflow,
        // subtraction: decrement exponent by LZP offset (using two's complement)
        {
            val lzpOffset = Mux(io.expDiff(0),
                lzOffset,
                Mux(~fixPredictionEnable && overflow, lzMinus1, lzPredict)
            )
            ~(0.U(3.W) ## lzpOffset)
        }
    )
    val exponentCarryIn = Mux(closeop === 0.U,
        // addition: carry in if overflow occurred
        carry | overflow,
        // subtraction: carry in for two's complement (except specific cases)
        Mux(io.expDiff(0), 1.U, Mux(fixPredictionEnable && !overflow, 0.U, 1.U))
    )
    val (resultExponent, _) = Adder(biggerExponent, exponentAdjustment, exponentCarryIn, EXP_WIDTH)
    val resultSign = Mux(closeop === 1.U && carry === 0.U, !biggerSign, biggerSign)
    io.result := Cat(resultSign, resultExponent, resultMantissa(MANTISSA_HIGH+1, MANTISSA_LOW+1))
    
}

class FAdd extends Module {
    import FPUtils._
    import IEEE754Constants._

    val io = IO(new FAddIO())

    // Step 1: Decompose operands and compute exponent difference using parallel subtraction
    val (sign1, exp1, mant1) = floatBreakdown(io.src1)
    val (sign2, exp2, mant2) = floatBreakdown(io.src2)

    // Parallel exponent subtraction for absolute difference
    val (expDiff1, carry1) = Adder(exp1, ~exp2, 1.U, 8)
    val (expDiff2, carry2) = Adder(exp2, ~exp1, 1.U, 8)

    // Select based on carry (overflow indicates which was larger)
    val expDiff = Mux(carry1.asBool, expDiff1, expDiff2)
    val biggerSrc = Mux(carry1.asBool, io.src1, io.src2)
    val smallerSrc = Mux(carry1.asBool, io.src2, io.src1)
    
    // Path operation is always the same as io.op
    // For addition: commutative, so order doesn't matter
    // For subtraction: we'll fix the sign later if operands were swapped
    val pathOp = io.op

    // Step 2: Path selection - Far if expDiff > 1, Close if expDiff <= 1
    // expDiff > 1 means expDiff >= 2, so check if any bit [7:1] is set
    val isFarPath = expDiff(7, 1).orR  // Check if bits [7:1] contain any 1

    // Step 3: Instantiate both path modules
    val farPath = Module(new FAddFarPath)
    val closePath = Module(new FAddClosePath)

    // Connect Far path
    farPath.io.biggerSrc := biggerSrc
    farPath.io.smallerSrc := smallerSrc
    farPath.io.op := pathOp
    farPath.io.expDiff := expDiff

    // Connect Close path
    closePath.io.biggerSrc := biggerSrc
    closePath.io.smallerSrc := smallerSrc
    closePath.io.op := pathOp
    closePath.io.expDiff := expDiff

    // Step 4: Select result based on path selection
    val pathResult = Mux(isFarPath, farPath.io.result, closePath.io.result)
    
    // If operands were swapped (carry1=false) and operation is subtraction (io.op=true),
    // we need to flip the result sign because: src1 - src2 = -(src2 - src1)
    val needSignFlip = !carry1.asBool && io.op
    val resultWithCorrectSign = Mux(needSignFlip, 
        Cat(~pathResult(31), pathResult(30, 0)),  // Flip sign bit
        pathResult
    )
    
    io.result := resultWithCorrectSign
}