import chisel3._
import chisel3.util._

// Constants for floating-point multiplication
object FMulConstants {
    val MANTISSA_WIDTH = 23
    val MANTISSA_WITH_HIDDEN = 24        // Mantissa + implicit leading 1
    val MANTISSA_EXTENDED_WIDTH = 25     // Extended for alignment: 1 + 24-bit mantissa
    val PARTIAL_PRODUCT_COUNT = 13       // Number of Booth-2 partial products for 24-bit multiplier
    val PARTIAL_PRODUCT_WIDTH = 48       // Width of each partial product (2 * MANTISSA_WITH_HIDDEN)
    val WALLACE_CIN_WIDTH = 10           // Carry-in bits for Wallace tree
    val WALLACE_COUT_WIDTH = 11          // Carry-out bits from Wallace tree
    val EXP_BIAS = 127                   // IEEE 754 single-precision exponent bias
    val EXP_BIAS_ADJUST_NORM = 130       // Bias adjustment when normalization +1: 256 - 127 + 1 = 130
    val EXP_BIAS_ADJUST_NO_NORM = 129    // Bias adjustment when no normalization: 256 - 127 = 129
}

object FMulUtils {
    def booth2(src1: UInt, src2: UInt): (UInt, UInt) = {
        val n = src1.getWidth
        val code = WireDefault(0.U(n.W))
        switch(src2){
            is(0.U) { code := 0.U }
            is(1.U) { code := src1 }
            is(2.U) { code := src1 }
            is(3.U) { code := src1 << 1 }
            is(4.U) { code := ~(src1 << 1) }
            is(5.U) { code := ~src1 }
            is(6.U) { code := ~src1 }
            is(7.U) { code := 0.U }
        }
        val add_1 = src2(2) && !src2.andR // 4, 5, 6
        (code, add_1)
    }
    def csa(src1: UInt, src2: UInt, cin: UInt): (UInt, UInt) = {
        val sum = src1 ^ src2 ^ cin
        val cout = (src1 & src2) | (src1 & cin) | (src2 & cin)
        (sum, cout)
    }
    // 11 cin cannot be realized, so we need to cope this in the later stage
    def wallceTree13Cin10(src: UInt, cin: UInt): (UInt, UInt) = {
        assert(src.getWidth == 13, "src must have width 13")
        assert(cin.getWidth == 10, "cin must have width 10")
        // 13->9->6->4->3->2
        val sum = Wire(MixedVec( // level 1-5
            Vec(4, UInt(1.W)),
            Vec(3, UInt(1.W)),
            Vec(2, UInt(1.W)),
            Vec(1, UInt(1.W)),
            Vec(1, UInt(1.W))
        ))
        val cout = Wire(MixedVec( // level 1-5
            Vec(4, UInt(1.W)),
            Vec(3, UInt(1.W)),
            Vec(2, UInt(1.W)),
            Vec(1, UInt(1.W)),
            Vec(1, UInt(1.W))
        ))
        val s1 = MixedVecInit(
            VecInit(src(1), src(4), src(7), src(10)),
            VecInit(src(0), sum(0)(2), cin(1)),
            VecInit(sum(1)(0), cin(4)),
            VecInit(sum(2)(0)),
            VecInit(sum(3)(0))
        )
        val s2 = MixedVecInit(
            VecInit(src(2), src(5), src(8), src(11)),
            VecInit(sum(0)(0), sum(0)(3), cin(2)),
            VecInit(sum(1)(1), cin(5)),
            VecInit(sum(2)(1)),
            VecInit(cin(8))
        )
        val s3 = MixedVecInit(
            VecInit(src(3), src(6), src(9), src(12)),
            VecInit(sum(0)(1), cin(0), cin(3)),
            VecInit(sum(1)(2), cin(6)),
            VecInit(cin(7)),
            VecInit(cin(9))
        )
        for(i <- 0 until 5){
            for(j <- 0 until sum(i).length){
                val (s, c) = csa(s1(i)(j), s2(i)(j), s3(i)(j))
                sum(i)(j) := s
                cout(i)(j) := c
            }
        }
    
        (sum(4).asUInt, cout.asUInt)
    }
}

class FMulIO extends Bundle {
    val src1 = Input(UInt(32.W))
    val src2 = Input(UInt(32.W))
    val result = Output(UInt(32.W))
}
class FMul extends Module {
    import FPUtils._
    import FMulUtils._
    import FMulConstants._
    
    val io = IO(new FMulIO())
    
    // Step 1: Decompose operands into IEEE 754 fields
    val (sign1, exp1, mant1) = floatBreakdown(io.src1)
    val (sign2, exp2, mant2) = floatBreakdown(io.src2)
    
    // Step 2: Extend mantissas with implicit leading 1 (1.mantissa format)
    val mant1Extended = 1.U(MANTISSA_EXTENDED_WIDTH.W) ## mant1
    val mant2Extended = 1.U(MANTISSA_EXTENDED_WIDTH.W) ## mant2
    
    // Step 3: Generate partial products using Booth-2 encoding
    // Booth-2 generates 13 partial products from 24-bit multiplier (ceil(24/2) + 1 for sign)
    val ppBooth = Wire(Vec(PARTIAL_PRODUCT_COUNT, UInt(PARTIAL_PRODUCT_WIDTH.W)))
    val add1Booth = Wire(Vec(PARTIAL_PRODUCT_COUNT, UInt(1.W)))
    
    // First partial product: special case for LSB
    ppBooth(0) := Mux(mant2(0), ~mant1Extended, 0.U(PARTIAL_PRODUCT_WIDTH.W))
    add1Booth(0) := mant2(0)
    
    // Remaining partial products using Booth-2 encoding
    for(i <- 1 until PARTIAL_PRODUCT_COUNT){
        val (pp, add1) = booth2((mant1Extended << (2*i-1)).take(PARTIAL_PRODUCT_WIDTH), mant2Extended(2*i, 2*i-2))
        ppBooth(i) := pp
        add1Booth(i) := add1
    }
    
    // Step 4: Wallace tree reduction - reduce 13 partial products to 2 vectors
    val sumWallce  = Wire(Vec(PARTIAL_PRODUCT_WIDTH, UInt(1.W)))
    val coutWallce = Wire(Vec(PARTIAL_PRODUCT_WIDTH, UInt(WALLACE_COUT_WIDTH.W)))
    
    for(i <- 0 until PARTIAL_PRODUCT_WIDTH){
        // Carry-in: first column uses Booth correction bits, others use previous carry-out
        val cin = (if(i == 0) VecInit(add1Booth.take(WALLACE_CIN_WIDTH)).asUInt else coutWallce(i-1)(WALLACE_CIN_WIDTH - 1, 0))
        val (sum, cout) = wallceTree13Cin10(VecInit(ppBooth.map(_(i))).asUInt, cin)
        sumWallce(i) := sum
        coutWallce(i) := cout
    }
    
    // Step 5: Final addition of Wallace tree outputs
    val addSrc1 = sumWallce.asUInt
    val addSrc2 = VecInit(coutWallce.map(_(WALLACE_CIN_WIDTH))).asUInt(PARTIAL_PRODUCT_WIDTH - 2, 0) ## add1Booth(WALLACE_CIN_WIDTH)
    val addCin = add1Booth(WALLACE_COUT_WIDTH)
    val (adderSum, _) = Adder(addSrc1, addSrc2, addCin, PARTIAL_PRODUCT_WIDTH)
    
    // Step 6: Calculate result exponent (exp1 + exp2, bias adjustment applied later)
    val (resultExponentTemp, resultExponentCarry) = Adder(exp1, exp2, 0.U, 8)

    // Step 7: Normalization - check if product >= 2.0 (MSB set)
    // Note: adderSum may be 1 less than actual due to pending Booth correction (add1Booth(12))
    val regularPlus1 = adderSum(PARTIAL_PRODUCT_WIDTH - 1) === 1.U

    // Step 8: Extract mantissa bits with rounding information
    // Select appropriate bit range based on normalization (shift by 1 if regularPlus1)
    val adderSumAndR20 = Mux(regularPlus1, 
        adderSum(PARTIAL_PRODUCT_WIDTH - 1, MANTISSA_WIDTH - 1) ## adderSum(MANTISSA_WIDTH - 2, 0).andR, 
        adderSum(PARTIAL_PRODUCT_WIDTH - 2, MANTISSA_WIDTH - 2) ## adderSum(MANTISSA_WIDTH - 3, 0).andR)
    val adderSumOrR20 = Mux(regularPlus1, 
        adderSum(PARTIAL_PRODUCT_WIDTH - 1, MANTISSA_WIDTH - 1) ## adderSum(MANTISSA_WIDTH - 2, 0).orR, 
        adderSum(PARTIAL_PRODUCT_WIDTH - 2, MANTISSA_WIDTH - 2) ## adderSum(MANTISSA_WIDTH - 3, 0).orR)
    
    // Step 9: Compute Guard, Round, Sticky bits for IEEE 754 rounding
    // Must account for potential +1 from remaining Booth correction (add1Booth(12))
    val lastBoothCorrection = add1Booth(PARTIAL_PRODUCT_COUNT - 1)
    val regularizeMantissaS = (!adderSumAndR20(0) || lastBoothCorrection === 0.U) && adderSumOrR20(0)
    val regularizeMantissaR = adderSumAndR20(1) ^ (adderSumAndR20(0) && lastBoothCorrection === 1.U)
    val regularizeMantissaG = adderSumAndR20(2) ^ (adderSumAndR20(1, 0).andR && lastBoothCorrection === 1.U)
    val regularizeMantissaLSB = adderSumAndR20(3) ^ (adderSumAndR20(2, 0).andR && lastBoothCorrection === 1.U)
    
    // Step 10: Round to nearest even - round up if G && (R || S || LSB)
    val roundOffPlus1 = regularizeMantissaG && (regularizeMantissaR || regularizeMantissaS || regularizeMantissaLSB)
    val wallceFix1 = adderSumAndR20(2, 0).andR && lastBoothCorrection === 1.U
    val (resultMantissaRoundOff, _) = Adder(adderSumAndR20(26, 3), 0.U, roundOffPlus1 || wallceFix1, MANTISSA_WITH_HIDDEN)
    
    // Step 11: Handle overflow from rounding (all mantissa bits were 1 and we rounded up)
    val overflow = adderSumAndR20(26, 3).andR & (roundOffPlus1 || wallceFix1)
    val resultMantissa = Cat(overflow, resultMantissaRoundOff) >> overflow
    
    // Step 12: Compute result sign (XOR of input signs)
    val resultSign = sign1 ^ sign2
    
    // Step 13: Compute final exponent with bias adjustment
    // exp1 + exp2 - 127 + normalization_adjustment
    // Using two's complement: +130 = -126 (mod 256), +129 = -127 (mod 256)
    val (resultExponent, _) = Adder(
        resultExponentTemp, 
        Mux(regularPlus1 || overflow, EXP_BIAS_ADJUST_NORM.U(8.W), EXP_BIAS_ADJUST_NO_NORM.U(8.W)),
        regularPlus1 && overflow, 
        8
    )
    
    // Step 14: Assemble final IEEE 754 result
    io.result := Cat(resultSign, resultExponent(7, 0), resultMantissa(MANTISSA_WIDTH - 1, 0))

}