import chisel3._
import chisel3.util._

object FPUtils {
    def floatBreakdown(x: UInt): (Bool, UInt, UInt) = {
        val sign = x(31)
        val exponent = x(30, 23)
        val mantissa = x(22, 0)
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
    val io = IO(new FAddFarPathIO())
    // break down the float into sign, exponent, and mantissa
    val (biggerSign, biggerExponent, biggerMantissa) = floatBreakdown(io.biggerSrc)
    val (smallerSign, smallerExponent, smallerMantissa) = floatBreakdown(io.smallerSrc)
    val farop = biggerSign ^ smallerSign ^ io.op
    val (biggerMantissaSB, smallerMantissaSB) = (Cat(1.U, biggerMantissa), Cat(1.U, smallerMantissa))
    val biggerMantissaFull = Cat(biggerMantissaSB, 0.U(3.W))

    val smallerMantissaExtended = Cat(smallerMantissaSB, 0.U(32.W))
    val smallerMantissaExtendedShifted = Wire(UInt(56.W))
    smallerMantissaExtendedShifted := smallerMantissaExtended >> io.expDiff
    // align the smaller mantissa to the bigger mantissa, save the GRS bits
    val smallerMantissaExtendedShiftedLSB = smallerMantissaExtendedShifted(55, 32)
    val smallerMantissaExtendedShiftedGR = smallerMantissaExtendedShifted(31, 30)
    val isLargeShift = io.expDiff(7, 5).orR
    val smallerMantissaExtendedShiftedS = Mux(isLargeShift, smallerMantissa.orR, smallerMantissaExtendedShifted(29, 0).orR)
    val smallerMantissaFull = Cat(smallerMantissaExtendedShiftedLSB, smallerMantissaExtendedShiftedGR, smallerMantissaExtendedShiftedS)
    // add the two mantissas
    val (sum, carry) = Adder(biggerMantissaFull, Mux(farop === 1.U, ~smallerMantissaFull, smallerMantissaFull), farop, 27)
    // regularize 1
    val regularMinus1 = sum(26) === 0.U
    val regularPlus1 = carry === 1.U
    val resultMantissaRegularized = Mux(regularMinus1, sum << 1, Mux(regularPlus1, sum >> 1, sum))
    val (resultMantissaSB, resultMantissaG, resultMantissaR, resultMantissaS) = (resultMantissaRegularized(26, 3), resultMantissaRegularized(2), resultMantissaRegularized(1), resultMantissaRegularized(0))
    // round off
    val roundPlus1 = resultMantissaG && (resultMantissaR || resultMantissaS || resultMantissaSB(0))
    val (resultMantissa, carry2) = Adder(resultMantissaSB, roundPlus1, 0.U, 24)
    // regularize 2
    val overflow = carry2 === 1.U
    val resultMantissaFinal = Cat(carry2, resultMantissa) >> overflow
    // calculate the exponent
    val (resultExponent, _) = Adder(biggerExponent, 
        Cat(Fill(7, regularMinus1 && !overflow), regularPlus1 && overflow),
        regularPlus1 || (regularMinus1 ^ overflow),
        8
    )
    val resultSign = biggerSign
    io.result := Cat(resultSign, resultExponent, resultMantissaFinal(22, 0))
}

class FAdd extends Module {
    import FPUtils._
    val io = IO(new FAddIO())
    val (sign1, exponent1, mantissa1) = floatBreakdown(io.src1)
    val (sign2, exponent2, mantissa2) = floatBreakdown(io.src2)
    val (expDiff1, src1IsBgeq) = Adder(exponent1, ~exponent2, 1.U, 8)
    val (expDiff2, src2IsBgeq) = Adder(exponent2, ~exponent1, 1.U, 8)
    val expDiff = Mux(src1IsBgeq === 1.U, expDiff1, expDiff2)
    // get the bigger one and the smaller one    
    val (biggerSrc, smallerSrc) = if (src1IsBgeq == 1.U) (exponent1, exponent2) else (exponent2, exponent1)
    val (biggerSign, biggerExponent, biggerMantissa) = floatBreakdown(biggerSrc)
    val (smallerSign, smallerExponent, smallerMantissa) = floatBreakdown(smallerSrc)
    /* far path */



    
}