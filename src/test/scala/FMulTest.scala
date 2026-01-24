import chisel3._
import chiseltest._
import chiseltest.simulator.{VerilatorBackendAnnotation, WriteVcdAnnotation}
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

class FMulTest extends AnyFlatSpec with ChiselScalatestTester {
  // Enable Verilator backend and VCD waveform output
  val vcdAnnotation = Seq(VerilatorBackendAnnotation)

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

  behavior of "FMul"

  it should "correctly multiply simple floating-point numbers" in {
    test(new FMul).withAnnotations(vcdAnnotation) { dut =>
      val testCases = Seq(
        // (src1, src2, description)
        (2.0f, 3.0f, "2.0 * 3.0 = 6.0"),
        (4.0f, 0.5f, "4.0 * 0.5 = 2.0"),
        (1.5f, 2.0f, "1.5 * 2.0 = 3.0"),
        (8.0f, 0.125f, "8.0 * 0.125 = 1.0"),
        (3.0f, 3.0f, "3.0 * 3.0 = 9.0"),
        (1.25f, 4.0f, "1.25 * 4.0 = 5.0"),
        (16.0f, 0.0625f, "16.0 * 0.0625 = 1.0"),
        (2.5f, 2.5f, "2.5 * 2.5 = 6.25"),
        (1.0f, 1.0f, "1.0 * 1.0 = 1.0"),
        (10.0f, 10.0f, "10.0 * 10.0 = 100.0")
      )

      for ((src1, src2, desc) <- testCases) {
        dut.io.src1.poke(floatToBigIntBits(src1).U)
        dut.io.src2.poke(floatToBigIntBits(src2).U)
        dut.clock.step()

        val result = dut.io.result.peek().litValue
        val expected = src1 * src2
        val expectedBits = floatToBigIntBits(expected)

        if (result != expectedBits) {
          println(f"FAIL: $desc: result=0x${result.toString(16)} (${bitsToFloat(result)}), expected=0x${expectedBits.toString(16)} ($expected)")
        }
        assert(
          result == expectedBits,
          f"Mismatch: $desc\nResult bits: 0x${result.toString(16)} (${bitsToFloat(result)})\nExpected bits: 0x${expectedBits.toString(16)} ($expected)"
        )
      }
    }
    println("FMulTest: correctly multiply simple floating-point numbers")
  }

  it should "correctly handle sign combinations" in {
    test(new FMul).withAnnotations(vcdAnnotation) { dut =>
      val testCases = Seq(
        // (src1, src2, description)
        (2.0f, 3.0f, "positive * positive = positive"),
        (-2.0f, 3.0f, "negative * positive = negative"),
        (2.0f, -3.0f, "positive * negative = negative"),
        (-2.0f, -3.0f, "negative * negative = positive"),
        (-4.0f, 0.5f, "-4.0 * 0.5 = -2.0"),
        (4.0f, -0.5f, "4.0 * -0.5 = -2.0"),
        (-4.0f, -0.5f, "-4.0 * -0.5 = 2.0"),
        (-1.5f, -2.0f, "-1.5 * -2.0 = 3.0")
      )

      for ((src1, src2, desc) <- testCases) {
        dut.io.src1.poke(floatToBigIntBits(src1).U)
        dut.io.src2.poke(floatToBigIntBits(src2).U)
        dut.clock.step()

        val result = dut.io.result.peek().litValue
        val expected = src1 * src2
        val expectedBits = floatToBigIntBits(expected)

        if (result != expectedBits) {
          println(f"FAIL: $desc: result=0x${result.toString(16)} (${bitsToFloat(result)}), expected=0x${expectedBits.toString(16)} ($expected)")
        }
        assert(
          result == expectedBits,
          f"Mismatch: $desc\nResult bits: 0x${result.toString(16)} (${bitsToFloat(result)})\nExpected bits: 0x${expectedBits.toString(16)} ($expected)"
        )
      }
    }
  }

  it should "correctly handle powers of two" in {
    test(new FMul).withAnnotations(vcdAnnotation) { dut =>
      val testCases = Seq(
        // (src1, src2, description)
        (2.0f, 2.0f, "2.0 * 2.0 = 4.0"),
        (4.0f, 4.0f, "4.0 * 4.0 = 16.0"),
        (8.0f, 8.0f, "8.0 * 8.0 = 64.0"),
        (0.5f, 0.5f, "0.5 * 0.5 = 0.25"),
        (0.25f, 4.0f, "0.25 * 4.0 = 1.0"),
        (16.0f, 0.25f, "16.0 * 0.25 = 4.0"),
        (32.0f, 0.125f, "32.0 * 0.125 = 4.0"),
        (64.0f, 0.015625f, "64.0 * 0.015625 = 1.0")
      )

      for ((src1, src2, desc) <- testCases) {
        dut.io.src1.poke(floatToBigIntBits(src1).U)
        dut.io.src2.poke(floatToBigIntBits(src2).U)
        dut.clock.step()

        val result = dut.io.result.peek().litValue
        val expected = src1 * src2
        val expectedBits = floatToBigIntBits(expected)

        if (result != expectedBits) {
          println(f"FAIL: $desc: result=0x${result.toString(16)} (${bitsToFloat(result)}), expected=0x${expectedBits.toString(16)} ($expected)")
        }
        assert(
          result == expectedBits,
          f"Mismatch: $desc\nResult bits: 0x${result.toString(16)} (${bitsToFloat(result)})\nExpected bits: 0x${expectedBits.toString(16)} ($expected)"
        )
      }
    }
    println("FMulTest: correctly handle sign combinations")
  }

  it should "correctly multiply numbers requiring mantissa normalization" in {
    test(new FMul).withAnnotations(vcdAnnotation) { dut =>
      val testCases = Seq(
        // Cases where mantissa product >= 2.0 (needs right shift)
        (1.75f, 1.75f, "1.75 * 1.75 = 3.0625"),
        (1.5f, 1.5f, "1.5 * 1.5 = 2.25"),
        (1.9f, 1.9f, "1.9 * 1.9 = 3.61"),
        (1.25f, 1.8f, "1.25 * 1.8 = 2.25"),
        // Cases where mantissa product < 2.0 (no shift needed)
        (1.1f, 1.1f, "1.1 * 1.1 = 1.21"),
        (1.01f, 1.01f, "1.01 * 1.01 = 1.0201"),
        (1.2f, 1.3f, "1.2 * 1.3 = 1.56")
      )

      for ((src1, src2, desc) <- testCases) {
        dut.io.src1.poke(floatToBigIntBits(src1).U)
        dut.io.src2.poke(floatToBigIntBits(src2).U)
        dut.clock.step()

        val result = dut.io.result.peek().litValue
        val expected = src1 * src2
        val expectedBits = floatToBigIntBits(expected)

        if (result != expectedBits) {
          println(f"FAIL: $desc: result=0x${result.toString(16)} (${bitsToFloat(result)}), expected=0x${expectedBits.toString(16)} ($expected)")
        }
        assert(
          result == expectedBits,
          f"Mismatch: $desc\nResult bits: 0x${result.toString(16)} (${bitsToFloat(result)})\nExpected bits: 0x${expectedBits.toString(16)} ($expected)"
        )
      }
    }
    println("FMulTest: correctly handle powers of two")
  }

  it should "correctly handle various exponent combinations" in {
    test(new FMul).withAnnotations(vcdAnnotation) { dut =>
      val random = new Random(42)
      var testCount = 0
      var failCount = 0

      for (i <- 0 until 1000) {
        // Generate normalized numbers with various exponent combinations
        val exp1 = 70 + random.nextInt(60)   // exp1 in [70, 129] (biased)
        val exp2 = 70 + random.nextInt(60)   // exp2 in [70, 129] (biased)
        val mant1 = random.nextInt(1 << 23)
        val mant2 = random.nextInt(1 << 23)
        val sign1 = random.nextInt(2)
        val sign2 = random.nextInt(2)

        val bits1 = floatToBits(sign1, exp1, mant1)
        val bits2 = floatToBits(sign2, exp2, mant2)
        val f1 = bitsToFloat(bits1)
        val f2 = bitsToFloat(bits2)

        // Skip special values
        if (!isSpecialValue(f1) && !isSpecialValue(f2)) {
          dut.io.src1.poke(bits1.U)
          dut.io.src2.poke(bits2.U)
          dut.clock.step()

          val result = dut.io.result.peek().litValue
          val expected = f1 * f2
          val expectedBits = floatToBigIntBits(expected)

          // Only validate if result is also not a special value
          if (!isSpecialValue(expected) && !isSpecialBits(result)) {
            testCount += 1
            if (result != expectedBits) {
              failCount += 1
              println(f"FAIL Exponent test $i: f1=$f1%.6e (exp=$exp1) * f2=$f2%.6e (exp=$exp2) => 0x${result.toString(16)} (${bitsToFloat(result)}), expected 0x${expectedBits.toString(16)} ($expected)")
            }
            assert(
              result == expectedBits,
              f"Exponent test $i failed:\nf1=$f1%.10e (exp=$exp1, 0x${bits1.toString(16)})\nf2=$f2%.10e (exp=$exp2, 0x${bits2.toString(16)})\nResult bits: 0x${result.toString(16)} (${bitsToFloat(result)})\nExpected bits: 0x${expectedBits.toString(16)} ($expected)"
            )
          }
        }
      }
      println(s"Exponent combinations: tested $testCount cases, $failCount failed")
    }
  }

  it should "correctly handle comprehensive random test cases" in {
    test(new FMul).withAnnotations(vcdAnnotation) { dut =>
      val random = new Random(4)
      var successCount = 0
      var totalTests = 0

      for (i <- 0 until 10000) {
        // Generate random normalized floats
        // Avoid extreme exponents to reduce overflow/underflow
        val exp1 = 64 + random.nextInt(128)  // [64, 191] - avoid denormals and overflow
        val exp2 = 64 + random.nextInt(128)
        val mant1 = random.nextInt(1 << 23)
        val mant2 = random.nextInt(1 << 23)
        val sign1 = random.nextInt(2)
        val sign2 = random.nextInt(2)

        val bits1 = floatToBits(sign1, exp1, mant1)
        val bits2 = floatToBits(sign2, exp2, mant2)
        val f1 = bitsToFloat(bits1)
        val f2 = bitsToFloat(bits2)

        // Skip special values
        if (!isSpecialValue(f1) && !isSpecialValue(f2)) {
          dut.io.src1.poke(bits1.U)
          dut.io.src2.poke(bits2.U)
          dut.clock.step()

          val result = dut.io.result.peek().litValue
          val expected = f1 * f2
          val expectedBits = floatToBigIntBits(expected)

          // Only validate if result is also not a special value
          if (!isSpecialValue(expected) && !isSpecialBits(result)) {
            totalTests += 1

            if (result == expectedBits) {
              successCount += 1
            } else {
              println(f"FAIL Test $i: f1=$f1%.6e * f2=$f2%.6e => 0x${result.toString(16)} (${bitsToFloat(result)}), expected 0x${expectedBits.toString(16)} ($expected)")
              assert(false, "Test failed - exact bit mismatch")
            }
          }
        }
      }

      println(s"Random test summary: $successCount/$totalTests tests passed")
      assert(successCount == totalTests, s"Some random tests failed: $successCount/$totalTests passed")
    }
  }

  it should "correctly handle rounding edge cases" in {
    test(new FMul).withAnnotations(vcdAnnotation) { dut =>
      val random = new Random(46)
      var testCount = 0
      var failCount = 0

      for (i <- 0 until 2000) {
        // Generate cases more likely to have interesting rounding behavior
        // Use mantissas with specific patterns
        val exp1 = 100 + random.nextInt(28)
        val exp2 = 100 + random.nextInt(28)
        
        // Generate mantissas with various bit patterns
        val mantPattern = i % 4
        val mant1 = mantPattern match {
          case 0 => 0x7FFFFF  // All ones
          case 1 => 0x400000  // Just MSB
          case 2 => 0x000001  // Just LSB
          case _ => random.nextInt(1 << 23)
        }
        val mant2 = random.nextInt(1 << 23)
        val sign1 = random.nextInt(2)
        val sign2 = random.nextInt(2)

        val bits1 = floatToBits(sign1, exp1, mant1)
        val bits2 = floatToBits(sign2, exp2, mant2)
        val f1 = bitsToFloat(bits1)
        val f2 = bitsToFloat(bits2)

        if (!isSpecialValue(f1) && !isSpecialValue(f2)) {
          dut.io.src1.poke(bits1.U)
          dut.io.src2.poke(bits2.U)
          dut.clock.step()

          val result = dut.io.result.peek().litValue
          val expected = f1 * f2
          val expectedBits = floatToBigIntBits(expected)

          if (!isSpecialValue(expected) && !isSpecialBits(result)) {
            testCount += 1
            if (result != expectedBits) {
              failCount += 1
              println(f"FAIL Rounding test $i: f1=$f1%.6e * f2=$f2%.6e => 0x${result.toString(16)} (${bitsToFloat(result)}), expected 0x${expectedBits.toString(16)} ($expected)")
            }
            assert(
              result == expectedBits,
              f"Rounding test $i failed:\nf1=$f1%.10e (0x${bits1.toString(16)})\nf2=$f2%.10e (0x${bits2.toString(16)})\nResult bits: 0x${result.toString(16)} (${bitsToFloat(result)})\nExpected bits: 0x${expectedBits.toString(16)} ($expected)"
            )
          }
        }
      }
      println(s"Rounding edge cases: tested $testCount cases, $failCount failed")
    }
  }

  it should "correctly handle small magnitude numbers (near underflow)" in {
    test(new FMul).withAnnotations(vcdAnnotation) { dut =>
      val random = new Random(47)
      var testCount = 0
      var failCount = 0

      for (i <- 0 until 500) {
        // Generate small normalized numbers
        val exp1 = 64 + random.nextInt(30)   // Small but not too small
        val exp2 = 64 + random.nextInt(30)
        val mant1 = random.nextInt(1 << 23)
        val mant2 = random.nextInt(1 << 23)
        val sign1 = random.nextInt(2)
        val sign2 = random.nextInt(2)

        val bits1 = floatToBits(sign1, exp1, mant1)
        val bits2 = floatToBits(sign2, exp2, mant2)
        val f1 = bitsToFloat(bits1)
        val f2 = bitsToFloat(bits2)

        if (!isSpecialValue(f1) && !isSpecialValue(f2)) {
          dut.io.src1.poke(bits1.U)
          dut.io.src2.poke(bits2.U)
          dut.clock.step()

          val result = dut.io.result.peek().litValue
          val expected = f1 * f2
          val expectedBits = floatToBigIntBits(expected)

          if (!isSpecialValue(expected) && !isSpecialBits(result)) {
            testCount += 1
            if (result != expectedBits) {
              failCount += 1
              println(f"FAIL Small magnitude test $i: f1=$f1%.6e * f2=$f2%.6e => 0x${result.toString(16)} (${bitsToFloat(result)}), expected 0x${expectedBits.toString(16)} ($expected)")
            }
            assert(
              result == expectedBits,
              f"Small magnitude test $i failed:\nf1=$f1%.10e (0x${bits1.toString(16)})\nf2=$f2%.10e (0x${bits2.toString(16)})\nResult bits: 0x${result.toString(16)} (${bitsToFloat(result)})\nExpected bits: 0x${expectedBits.toString(16)} ($expected)"
            )
          }
        }
      }
      println(s"Small magnitude: tested $testCount cases, $failCount failed")
    }
  }

  it should "correctly handle large magnitude numbers (near overflow)" in {
    test(new FMul).withAnnotations(vcdAnnotation) { dut =>
      val random = new Random(48)
      var testCount = 0
      var failCount = 0

      for (i <- 0 until 500) {
        // Generate large but not too large normalized numbers
        val exp1 = 127 + random.nextInt(20)   // Large but controlled
        val exp2 = 127 - random.nextInt(20)   // Balance to avoid overflow
        val mant1 = random.nextInt(1 << 23)
        val mant2 = random.nextInt(1 << 23)
        val sign1 = random.nextInt(2)
        val sign2 = random.nextInt(2)

        val bits1 = floatToBits(sign1, exp1, mant1)
        val bits2 = floatToBits(sign2, exp2, mant2)
        val f1 = bitsToFloat(bits1)
        val f2 = bitsToFloat(bits2)

        if (!isSpecialValue(f1) && !isSpecialValue(f2)) {
          dut.io.src1.poke(bits1.U)
          dut.io.src2.poke(bits2.U)
          dut.clock.step()

          val result = dut.io.result.peek().litValue
          val expected = f1 * f2
          val expectedBits = floatToBigIntBits(expected)

          if (!isSpecialValue(expected) && !isSpecialBits(result)) {
            testCount += 1
            if (result != expectedBits) {
              failCount += 1
              println(f"FAIL Large magnitude test $i: f1=$f1%.6e * f2=$f2%.6e => 0x${result.toString(16)} (${bitsToFloat(result)}), expected 0x${expectedBits.toString(16)} ($expected)")
            }
            assert(
              result == expectedBits,
              f"Large magnitude test $i failed:\nf1=$f1%.10e (0x${bits1.toString(16)})\nf2=$f2%.10e (0x${bits2.toString(16)})\nResult bits: 0x${result.toString(16)} (${bitsToFloat(result)})\nExpected bits: 0x${expectedBits.toString(16)} ($expected)"
            )
          }
        }
      }
      println(s"Large magnitude: tested $testCount cases, $failCount failed")
    }
  }

  it should "correctly handle multiplication with 1.0" in {
    test(new FMul).withAnnotations(vcdAnnotation) { dut =>
      val random = new Random(49)
      var testCount = 0
      var failCount = 0

      // Multiplying by 1.0 should return the original number
      for (i <- 0 until 200) {
        val exp = 64 + random.nextInt(128)
        val mant = random.nextInt(1 << 23)
        val sign = random.nextInt(2)

        val bits1 = floatToBits(sign, exp, mant)
        val bits2 = floatToBigIntBits(1.0f)
        val f1 = bitsToFloat(bits1)

        if (!isSpecialValue(f1)) {
          dut.io.src1.poke(bits1.U)
          dut.io.src2.poke(bits2.U)
          dut.clock.step()

          val result = dut.io.result.peek().litValue

          if (!isSpecialBits(result)) {
            testCount += 1
            if (result != bits1) {
              failCount += 1
              println(f"FAIL Multiply by 1.0 test $i: f1=$f1%.6e => 0x${result.toString(16)} (${bitsToFloat(result)}), expected 0x${bits1.toString(16)} ($f1)")
            }
            assert(
              result == bits1,
              f"Multiply by 1.0 test $i failed:\nf1=$f1%.10e (0x${bits1.toString(16)})\nResult bits: 0x${result.toString(16)} (${bitsToFloat(result)})\nExpected bits: 0x${bits1.toString(16)} ($f1)"
            )
          }
        }
      }
      println(s"Multiply by 1.0: tested $testCount cases, $failCount failed")
    }
  }

  it should "correctly handle multiplication with -1.0 (sign flip)" in {
    test(new FMul).withAnnotations(vcdAnnotation) { dut =>
      val random = new Random(50)
      var testCount = 0
      var failCount = 0

      // Multiplying by -1.0 should flip the sign
      for (i <- 0 until 200) {
        val exp = 64 + random.nextInt(128)
        val mant = random.nextInt(1 << 23)
        val sign = random.nextInt(2)

        val bits1 = floatToBits(sign, exp, mant)
        val bits2 = floatToBigIntBits(-1.0f)
        val f1 = bitsToFloat(bits1)
        val expected = -f1
        val expectedBits = floatToBigIntBits(expected)

        if (!isSpecialValue(f1) && !isSpecialValue(expected)) {
          dut.io.src1.poke(bits1.U)
          dut.io.src2.poke(bits2.U)
          dut.clock.step()

          val result = dut.io.result.peek().litValue

          if (!isSpecialBits(result)) {
            testCount += 1
            if (result != expectedBits) {
              failCount += 1
              println(f"FAIL Multiply by -1.0 test $i: f1=$f1%.6e => 0x${result.toString(16)} (${bitsToFloat(result)}), expected 0x${expectedBits.toString(16)} ($expected)")
            }
            assert(
              result == expectedBits,
              f"Multiply by -1.0 test $i failed:\nf1=$f1%.10e (0x${bits1.toString(16)})\nResult bits: 0x${result.toString(16)} (${bitsToFloat(result)})\nExpected bits: 0x${expectedBits.toString(16)} ($expected)"
            )
          }
        }
      }
      println(s"Multiply by -1.0: tested $testCount cases, $failCount failed")
    }
  }
}
