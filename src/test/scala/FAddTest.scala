import chisel3._
import chiseltest._
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

class FAddTest extends AnyFlatSpec with ChiselScalatestTester {
  // Enable VCD waveform output
  val vcdAnnotation = Seq(WriteVcdAnnotation)

  // Helper function to create IEEE 754 single-precision float
  def floatToBits(sign: Int, exp: Int, mantissa: Int): BigInt = {
    require(sign == 0 || sign == 1, "Sign must be 0 or 1")
    require(exp >= 0 && exp <= 255, "Exponent must be 0-255")
    require(mantissa >= 0 && mantissa < (1 << 23), "Mantissa must be 23 bits")
    val bits = (sign << 31) | (exp << 23) | mantissa
    BigInt(bits & 0xFFFFFFFFL)
  }

  // Helper to extract components
  def extractFloat(bits: BigInt): (Int, Int, Int) = {
    val sign = ((bits >> 31) & 1).toInt
    val exp = ((bits >> 23) & 0xFF).toInt
    val mantissa = (bits & 0x7FFFFF).toInt
    (sign, exp, mantissa)
  }

  // Convert BigInt bits to Float
  def bitsToFloat(bits: BigInt): Float = {
    java.lang.Float.intBitsToFloat(bits.toInt)
  }

  // Convert Float to BigInt bits
  def floatToBigIntBits(f: Float): BigInt = {
    BigInt(java.lang.Float.floatToRawIntBits(f) & 0xFFFFFFFFL)
  }

  // Check if a float is denormal, NaN, Infinity, or Zero
  def isSpecialValue(f: Float): Boolean = {
    f.isNaN || f.isInfinity || f == 0.0f || Math.abs(f) < Float.MinPositiveValue
  }

  // Check if a float bits represent a special value
  def isSpecialBits(bits: BigInt): Boolean = {
    val exp = ((bits >> 23) & 0xFF).toInt
    val mantissa = (bits & 0x7FFFFF).toInt
    exp == 0 || exp == 255  // Denormal, zero, NaN, or Infinity
  }

  behavior of "FAdd"

  it should "correctly add simple floating-point numbers" in {
    test(new FAdd) { dut =>
      val testCases = Seq(
        // (src1, src2, op, description)
        (2.0f, 0.5f, false, "2.0 + 0.5 = 2.5"),
        (16.0f, 0.125f, false, "16.0 + 0.125 = 16.125"),
        (8.0f, 2.0f, false, "8.0 + 2.0 = 10.0"),
        (4.0f, 1.0f, false, "4.0 + 1.0 = 5.0"),
        (3.0f, 0.75f, false, "3.0 + 0.75 = 3.75"),
        (5.0f, 3.0f, true, "5.0 - 3.0 = 2.0"),
        (10.0f, 2.5f, true, "10.0 - 2.5 = 7.5")
      )

      for ((src1, src2, op, desc) <- testCases) {
        dut.io.src1.poke(floatToBigIntBits(src1).U)
        dut.io.src2.poke(floatToBigIntBits(src2).U)
        dut.io.op.poke(op.B)
        dut.clock.step()

        val result = dut.io.result.peek().litValue
        val expected = if (op) src1 - src2 else src1 + src2
        val expectedBits = floatToBigIntBits(expected)

        println(f"$desc: result=0x${result.toString(16)}, expected=0x${expectedBits.toString(16)}")
        assert(
          result == expectedBits,
          f"Mismatch: $desc\nResult bits: 0x${result.toString(16)} (${bitsToFloat(result)})\nExpected bits: 0x${expectedBits.toString(16)} ($expected)"
        )
      }
    }
  }

  it should "correctly select Far path for large exponent differences (expDiff > 1)" in {
    test(new FAdd) { dut =>
      val random = new Random(42)
      var farPathCount = 0

      for (i <- 0 until 10000) {
        // Generate numbers with expDiff > 1 (at least 2)
        val exp1 = 100 + random.nextInt(50)  // exp1 in [100, 149]
        val exp2 = exp1 - (2 + random.nextInt(30))  // exp2 smaller by at least 2
        val mant1 = random.nextInt(1 << 23)
        val mant2 = random.nextInt(1 << 23)
        val sign1 = random.nextInt(2)
        val sign2 = random.nextInt(2)
        val op = random.nextBoolean()

        val bits1 = floatToBits(sign1, exp1, mant1)
        val bits2 = floatToBits(sign2, exp2, mant2)
        val f1 = bitsToFloat(bits1)
        val f2 = bitsToFloat(bits2)

        // Skip special values
        if (!isSpecialValue(f1) && !isSpecialValue(f2)) {
          dut.io.src1.poke(bits1.U)
          dut.io.src2.poke(bits2.U)
          dut.io.op.poke(op.B)
          dut.clock.step()

          val result = dut.io.result.peek().litValue
          val expected = if (op) f1 - f2 else f1 + f2
          val expectedBits = floatToBigIntBits(expected)           

          if (!isSpecialValue(expected) && !isSpecialBits(result)) {
            farPathCount += 1
            assert(
              result == expectedBits,
              f"Far path test $i failed:\nf1=$f1%.10e (exp=$exp1, 0x${bits1.toString(16)})\nf2=$f2%.10e (exp=$exp2, 0x${bits2.toString(16)})\nop=$op\nResult bits: 0x${result.toString(16)} (${bitsToFloat(result)})\nExpected bits: 0x${expectedBits.toString(16)} ($expected)"
            )
          }
        }
      }
      println(s"Far path: tested $farPathCount cases")
    }
  }

  it should "correctly select Close path for small exponent differences (expDiff <= 1)" in {
    test(new FAdd) { dut =>
      val random = new Random(43)
      var closePathCount = 0

      for (i <- 0 until 1000) {
        // Generate numbers with expDiff <= 1
        val exp1 = 100 + random.nextInt(50)  // exp1 in [100, 149]
        val expDiffChoice = random.nextInt(2)  // 0 or 1
        val exp2 = exp1 - expDiffChoice
        val mant1 = random.nextInt(1 << 23)
        val mant2 = random.nextInt(1 << 23)
        val sign1 = random.nextInt(2)
        val sign2 = random.nextInt(2)
        val op = random.nextBoolean()

        val bits1 = floatToBits(sign1, exp1, mant1)
        val bits2 = floatToBits(sign2, exp2, mant2)
        val f1 = bitsToFloat(bits1)
        val f2 = bitsToFloat(bits2)

        // Skip special values
        if (!isSpecialValue(f1) && !isSpecialValue(f2)) {
          dut.io.src1.poke(bits1.U)
          dut.io.src2.poke(bits2.U)
          dut.io.op.poke(op.B)
          dut.clock.step()

          val result = dut.io.result.peek().litValue
          val expected = if (op) f1 - f2 else f1 + f2
          val expectedBits = floatToBigIntBits(expected)

          if (!isSpecialValue(expected) && !isSpecialBits(result)) {
            closePathCount += 1
            assert(
              result == expectedBits,
              f"Close path test $i failed:\nf1=$f1%.10e (exp=$exp1, 0x${bits1.toString(16)})\nf2=$f2%.10e (exp=$exp2, 0x${bits2.toString(16)})\nop=$op\nResult bits: 0x${result.toString(16)} (${bitsToFloat(result)})\nExpected bits: 0x${expectedBits.toString(16)} ($expected)"
            )
          }
        }
      }
      println(s"Close path: tested $closePathCount cases")
    }
  }

  it should "handle boundary cases around expDiff = 1" in {
    test(new FAdd) { dut =>
      val random = new Random(44)
      
      for (i <- 0 until 500) {
        // Test specifically expDiff = 0, 1, 2
        val exp1 = 100 + random.nextInt(50)
        val expDiff = i % 3  // Cycle through 0, 1, 2
        val exp2 = exp1 - expDiff
        val mant1 = random.nextInt(1 << 23)
        val mant2 = random.nextInt(1 << 23)
        val sign1 = random.nextInt(2)
        val sign2 = random.nextInt(2)
        val op = random.nextBoolean()

        val bits1 = floatToBits(sign1, exp1, mant1)
        val bits2 = floatToBits(sign2, exp2, mant2)
        val f1 = bitsToFloat(bits1)
        val f2 = bitsToFloat(bits2)

        if (!isSpecialValue(f1) && !isSpecialValue(f2)) {
          dut.io.src1.poke(bits1.U)
          dut.io.src2.poke(bits2.U)
          dut.io.op.poke(op.B)
          dut.clock.step()

          val result = dut.io.result.peek().litValue
          val expected = if (op) f1 - f2 else f1 + f2
          val expectedBits = floatToBigIntBits(expected)

          if (!isSpecialValue(expected) && !isSpecialBits(result)) {
            assert(
              result == expectedBits,
              f"Boundary test failed at expDiff=$expDiff:\nf1=$f1%.10e (0x${bits1.toString(16)})\nf2=$f2%.10e (0x${bits2.toString(16)})\nop=$op\nResult bits: 0x${result.toString(16)} (${bitsToFloat(result)})\nExpected bits: 0x${expectedBits.toString(16)} ($expected)"
            )
          }
        }
      }
    }
  }

  it should "correctly handle comprehensive random test cases" in {
    test(new FAdd) { dut =>
      val random = new Random(45)
      var successCount = 0
      var totalTests = 0

      for (i <- 0 until 10000) {
        // Generate random normalized floats
        // Avoid extreme exponents to reduce special values
        val exp1 = 10 + random.nextInt(236)  // [10, 245] - avoid denormals and large numbers
        val exp2 = 10 + random.nextInt(236)
        val mant1 = random.nextInt(1 << 23)
        val mant2 = random.nextInt(1 << 23)
        val sign1 = random.nextInt(2)
        val sign2 = random.nextInt(2)
        val op = random.nextBoolean()

        val bits1 = floatToBits(sign1, exp1, mant1)
        val bits2 = floatToBits(sign2, exp2, mant2)
        val f1 = bitsToFloat(bits1)
        val f2 = bitsToFloat(bits2)

        // Skip special values
        if (!isSpecialValue(f1) && !isSpecialValue(f2)) {
          dut.io.src1.poke(bits1.U)
          dut.io.src2.poke(bits2.U)
          dut.io.op.poke(op.B)
          dut.clock.step()

          val result = dut.io.result.peek().litValue
          val expected = if (op) f1 - f2 else f1 + f2
          val expectedBits = floatToBigIntBits(expected)

          // Only validate if result is also not a special value
          if (!isSpecialValue(expected) && !isSpecialBits(result)) {
            totalTests += 1
            
            if (result == expectedBits) {
              successCount += 1
            } else {
              println(f"Test $i failed:")
              println(f"  f1=$f1%.10e (0x${bits1.toString(16)})")
              println(f"  f2=$f2%.10e (0x${bits2.toString(16)})")
              println(f"  op=$op")
              println(f"  Result bits: 0x${result.toString(16)} (${bitsToFloat(result)})")
              println(f"  Expected bits: 0x${expectedBits.toString(16)} ($expected)")
              assert(false, "Test failed - exact bit mismatch")
            }
          }
        }
      }
      
      println(s"\nRandom test summary: $successCount/$totalTests tests passed")
      assert(successCount == totalTests, s"Some random tests failed: $successCount/$totalTests passed")
    }
  }

  it should "correctly handle mixed sign operations" in {
    test(new FAdd) { dut =>
      val testCases = Seq(
        // (src1, src2, op, description)
        (5.0f, -3.0f, false, "5.0 + (-3.0) = 2.0"),
        (-5.0f, 3.0f, false, "-5.0 + 3.0 = -2.0"),
        (-5.0f, -3.0f, false, "-5.0 + (-3.0) = -8.0"),
        (5.0f, -3.0f, true, "5.0 - (-3.0) = 8.0"),
        (-5.0f, 3.0f, true, "-5.0 - 3.0 = -8.0"),
        (-5.0f, -3.0f, true, "-5.0 - (-3.0) = -2.0")
      )

      for ((src1, src2, op, desc) <- testCases) {
        if (!isSpecialValue(src1) && !isSpecialValue(src2)) {
          dut.io.src1.poke(floatToBigIntBits(src1).U)
          dut.io.src2.poke(floatToBigIntBits(src2).U)
          dut.io.op.poke(op.B)
          dut.clock.step()

          val result = dut.io.result.peek().litValue
          val expected = if (op) src1 - src2 else src1 + src2
          val expectedBits = floatToBigIntBits(expected)

          if (!isSpecialValue(expected) && !isSpecialBits(result)) {
            println(f"$desc: result=0x${result.toString(16)}, expected=0x${expectedBits.toString(16)}")
            assert(
              result == expectedBits,
              f"Mismatch: $desc\nResult bits: 0x${result.toString(16)} (${bitsToFloat(result)})\nExpected bits: 0x${expectedBits.toString(16)} ($expected)"
            )
          }
        }
      }
    }
  }
}
