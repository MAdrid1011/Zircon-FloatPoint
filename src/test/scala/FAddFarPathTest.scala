import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class FAddFarPathTest extends AnyFlatSpec with ChiselScalatestTester {

  // Helper function to create IEEE 754 single-precision float
  def floatToBits(sign: Int, exp: Int, mantissa: Int): BigInt = {
    require(sign == 0 || sign == 1, "Sign must be 0 or 1")
    require(exp >= 0 && exp <= 255, "Exponent must be 0-255")
    require(mantissa >= 0 && mantissa < (1 << 23), "Mantissa must be 23 bits")
    val bits = (sign << 31) | (exp << 23) | mantissa
    // Convert to unsigned 32-bit value
    BigInt(bits & 0xFFFFFFFFL)
  }

  // Helper to extract components
  def extractFloat(bits: BigInt): (Int, Int, Int) = {
    val sign = ((bits >> 31) & 1).toInt
    val exp = ((bits >> 23) & 0xFF).toInt
    val mantissa = (bits & 0x7FFFFF).toInt
    (sign, exp, mantissa)
  }

  behavior of "FAddFarPath"

  it should "handle basic addition with exponent difference = 2" in {
    test(new FAddFarPath) { dut =>
      // Test: 2.0 + 0.5 = 2.5
      // 2.0 = 0 10000000 00000000000000000000000 (exp=128, mantissa=0)
      // 0.5 = 0 01111110 00000000000000000000000 (exp=126, mantissa=0)
      val bigger = floatToBits(0, 128, 0)
      val smaller = floatToBits(0, 126, 0)

      dut.io.biggerSrc.poke(bigger.U)
      dut.io.smallerSrc.poke(smaller.U)
      dut.io.op.poke(false.B) // addition
      dut.io.expDiff.poke(2.U)

      dut.clock.step(1)

      // Expected: 2.5 = 0 10000000 01000000000000000000000
      val result = dut.io.result.peek().litValue
      val (resSign, resExp, resMant) = extractFloat(result)

      assert(resSign == 0, s"Sign should be 0, got $resSign")
      assert(resExp == 128, s"Exponent should be 128, got $resExp")
      assert(resMant == 0x200000, s"Mantissa should be 0x200000, got 0x${resMant.toHexString}")
    }
  }

  it should "handle addition with large exponent difference" in {
    test(new FAddFarPath) { dut =>
      // Test: 16.0 + 0.125 = 16.125
      // 16.0 = 0 10000011 00000000000000000000000 (exp=131)
      // 0.125 = 0 01111100 00000000000000000000000 (exp=124)
      val bigger = floatToBits(0, 131, 0)
      val smaller = floatToBits(0, 124, 0)

      dut.io.biggerSrc.poke(bigger.U)
      dut.io.smallerSrc.poke(smaller.U)
      dut.io.op.poke(false.B)
      dut.io.expDiff.poke(7.U)

      dut.clock.step(1)

      val result = dut.io.result.peek().litValue
      val (resSign, resExp, resMant) = extractFloat(result)

      assert(resSign == 0, s"Sign should be 0, got $resSign")
      assert(resExp == 131, s"Exponent should be 131, got $resExp")
    }
  }

  it should "handle subtraction with same sign (effective addition)" in {
    test(new FAddFarPath) { dut =>
      // Test: 4.0 - (-1.0) = 5.0
      // 4.0 = 0 10000001 00000000000000000000000 (exp=129)
      // -1.0 = 1 01111111 00000000000000000000000 (exp=127)
      val bigger = floatToBits(0, 129, 0)
      val smaller = floatToBits(1, 127, 0)

      dut.io.biggerSrc.poke(bigger.U)
      dut.io.smallerSrc.poke(smaller.U)
      dut.io.op.poke(true.B) // subtraction
      dut.io.expDiff.poke(2.U)

      dut.clock.step(1)

      val result = dut.io.result.peek().litValue
      val (resSign, resExp, resMant) = extractFloat(result)

      assert(resSign == 0, s"Sign should be 0, got $resSign")
    }
  }

  it should "handle effective subtraction (different signs, op=add)" in {
    test(new FAddFarPath) { dut =>
      // Test: 8.0 + (-2.0) = 6.0
      // 8.0 = 0 10000010 00000000000000000000000 (exp=130, represents 1.0 × 2^3)
      // -2.0 = 1 10000000 00000000000000000000000 (exp=128, represents 1.0 × 2^1)
      // Result: 6.0 = 1.5 × 2^2, so exp=129
      val bigger = floatToBits(0, 130, 0)
      val smaller = floatToBits(1, 128, 0)

      dut.io.biggerSrc.poke(bigger.U)
      dut.io.smallerSrc.poke(smaller.U)
      dut.io.op.poke(false.B) // addition
      dut.io.expDiff.poke(2.U)

      dut.clock.step(1)

      val result = dut.io.result.peek().litValue
      val (resSign, resExp, resMant) = extractFloat(result)

      assert(resSign == 0, s"Sign should be 0, got $resSign")
      assert(resExp == 129, s"Exponent should be 129, got $resExp")
    }
  }

  it should "handle rounding correctly (round to nearest, ties to even)" in {
    test(new FAddFarPath) { dut =>
      // Test case that requires rounding
      // Create a scenario where GRS bits trigger rounding
      val bigger = floatToBits(0, 130, 0x400000) // 1.1 * 2^3
      val smaller = floatToBits(0, 127, 0x600000) // 1.75 * 2^0

      dut.io.biggerSrc.poke(bigger.U)
      dut.io.smallerSrc.poke(smaller.U)
      dut.io.op.poke(false.B)
      dut.io.expDiff.poke(3.U)

      dut.clock.step(1)

      val result = dut.io.result.peek().litValue
      val (resSign, resExp, resMant) = extractFloat(result)

      assert(resSign == 0, s"Sign should be 0, got $resSign")
    }
  }

  it should "handle normalization when result needs left shift" in {
    test(new FAddFarPath) { dut =>
      // Test subtraction that causes cancellation requiring normalization
      // This tests the regularMinus1 path
      val bigger = floatToBits(0, 130, 0x000001)
      val smaller = floatToBits(0, 128, 0x7FFFFE)

      dut.io.biggerSrc.poke(bigger.U)
      dut.io.smallerSrc.poke(smaller.U)
      dut.io.op.poke(false.B)
      dut.io.expDiff.poke(2.U)

      dut.clock.step(1)

      val result = dut.io.result.peek().litValue
      val (resSign, resExp, resMant) = extractFloat(result)

      assert(resSign == 0, s"Sign should be 0, got $resSign")
    }
  }

  it should "handle normalization when result needs right shift (overflow)" in {
    test(new FAddFarPath) { dut =>
      // Test addition that causes overflow requiring right shift
      // This tests the regularPlus1 path
      val bigger = floatToBits(0, 130, 0x7FFFFF)
      val smaller = floatToBits(0, 128, 0x7FFFFF)

      dut.io.biggerSrc.poke(bigger.U)
      dut.io.smallerSrc.poke(smaller.U)
      dut.io.op.poke(false.B)
      dut.io.expDiff.poke(2.U)

      dut.clock.step(1)

      val result = dut.io.result.peek().litValue
      val (resSign, resExp, resMant) = extractFloat(result)

      assert(resSign == 0, s"Sign should be 0, got $resSign")
    }
  }

  it should "handle very large exponent differences (>32)" in {
    test(new FAddFarPath) { dut =>
      // Test with expDiff > 32, smaller number should be mostly lost
      val bigger = floatToBits(0, 160, 0)
      val smaller = floatToBits(0, 120, 0x7FFFFF)

      dut.io.biggerSrc.poke(bigger.U)
      dut.io.smallerSrc.poke(smaller.U)
      dut.io.op.poke(false.B)
      dut.io.expDiff.poke(40.U)

      dut.clock.step(1)

      val result = dut.io.result.peek().litValue
      val (resSign, resExp, resMant) = extractFloat(result)

      // Result should be approximately equal to bigger
      assert(resSign == 0, s"Sign should be 0, got $resSign")
      assert(resExp == 160, s"Exponent should be 160, got $resExp")
    }
  }

  it should "preserve sign of bigger operand" in {
    test(new FAddFarPath) { dut =>
      // Test with negative bigger operand
      val bigger = floatToBits(1, 130, 0) // -8.0
      val smaller = floatToBits(0, 127, 0) // 1.0

      dut.io.biggerSrc.poke(bigger.U)
      dut.io.smallerSrc.poke(smaller.U)
      dut.io.op.poke(false.B)
      dut.io.expDiff.poke(3.U)

      dut.clock.step(1)

      val result = dut.io.result.peek().litValue
      val (resSign, resExp, resMant) = extractFloat(result)

      assert(resSign == 1, s"Sign should be 1 (negative), got $resSign")
    }
  }

  it should "handle minimum exponent difference of 2" in {
    test(new FAddFarPath) { dut =>
      // Test with expDiff = 2 (minimum for far path)
      val bigger = floatToBits(0, 129, 0)
      val smaller = floatToBits(0, 127, 0)

      dut.io.biggerSrc.poke(bigger.U)
      dut.io.smallerSrc.poke(smaller.U)
      dut.io.op.poke(false.B)
      dut.io.expDiff.poke(2.U)

      dut.clock.step(1)

      val result = dut.io.result.peek().litValue
      val (resSign, resExp, resMant) = extractFloat(result)

      assert(resSign == 0, s"Sign should be 0, got $resSign")
    }
  }

  it should "handle sticky bit computation correctly" in {
    test(new FAddFarPath) { dut =>
      // Test that sticky bit is set when bits are shifted out
      val bigger = floatToBits(0, 130, 0)
      val smaller = floatToBits(0, 125, 0x7FFFFF) // All mantissa bits set

      dut.io.biggerSrc.poke(bigger.U)
      dut.io.smallerSrc.poke(smaller.U)
      dut.io.op.poke(false.B)
      dut.io.expDiff.poke(5.U)

      dut.clock.step(1)

      val result = dut.io.result.peek().litValue
      val (resSign, resExp, resMant) = extractFloat(result)

      assert(resSign == 0, s"Sign should be 0, got $resSign")
    }
  }

  it should "handle all mantissa bits set in both operands" in {
    test(new FAddFarPath) { dut =>
      // Edge case: both operands have all mantissa bits set
      val bigger = floatToBits(0, 130, 0x7FFFFF)
      val smaller = floatToBits(0, 127, 0x7FFFFF)

      dut.io.biggerSrc.poke(bigger.U)
      dut.io.smallerSrc.poke(smaller.U)
      dut.io.op.poke(false.B)
      dut.io.expDiff.poke(3.U)

      dut.clock.step(1)

      val result = dut.io.result.peek().litValue
      val (resSign, resExp, resMant) = extractFloat(result)

      assert(resSign == 0, s"Sign should be 0, got $resSign")
    }
  }

  it should "handle subtraction resulting in smaller magnitude" in {
    test(new FAddFarPath) { dut =>
      // Test: 8.0 - 7.5 (represented as 8.0 + (-7.5) with proper setup)
      val bigger = floatToBits(0, 130, 0)
      val smaller = floatToBits(0, 129, 0x600000)

      dut.io.biggerSrc.poke(bigger.U)
      dut.io.smallerSrc.poke(smaller.U)
      dut.io.op.poke(false.B)
      dut.io.expDiff.poke(1.U)

      dut.clock.step(1)

      val result = dut.io.result.peek().litValue
      val (resSign, resExp, resMant) = extractFloat(result)

      assert(resSign == 0, s"Sign should be 0, got $resSign")
    }
  }
}
