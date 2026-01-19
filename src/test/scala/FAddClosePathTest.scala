import chisel3._
import chiseltest._
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

class FAddClosePathTest extends AnyFlatSpec with ChiselScalatestTester {
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

  behavior of "FAddClosePath"

  it should "match Scala Float for basic addition cases with expDiff=0" in {
    test(new FAddClosePath).withAnnotations(vcdAnnotation) { dut =>
      val testCases = Seq(
        // (bigger, smaller, op, expDiff) - same exponent
        (1.75f, 1.25f, false, 0),    // 1.75 + 1.25 = 3.0 (both exp=127)
        (3.0f, 2.0f, false, 0),      // 3.0 + 2.0 = 5.0 (both exp=128)
        (1.5f, 1.25f, false, 0),     // 1.5 + 1.25 = 2.75 (both exp=127)
        (5.0f, 4.0f, false, 0),      // 5.0 + 4.0 = 9.0 (both exp=129)
        (7.0f, 6.0f, false, 0)       // 7.0 + 6.0 = 13.0 (both exp=129)
      )

      for ((bigger, smaller, isSubtract, expDiff) <- testCases) {
        val biggerBits = floatToBigIntBits(bigger)
        val smallerBits = floatToBigIntBits(smaller)

        dut.io.biggerSrc.poke(biggerBits.U)
        dut.io.smallerSrc.poke(smallerBits.U)
        dut.io.op.poke(isSubtract.B)
        dut.io.expDiff.poke(expDiff.U)

        dut.clock.step(1)

        val hwResult = dut.io.result.peek().litValue
        val hwFloat = bitsToFloat(hwResult)

        // Calculate reference using Scala Float
        val reference = if (isSubtract) bigger - smaller else bigger + smaller

        val hwBits = java.lang.Float.floatToRawIntBits(hwFloat)
        val refBits = java.lang.Float.floatToRawIntBits(reference)

        if (hwBits != refBits) {
          println(f"\n=== FAILED: $bigger + $smaller ===")
          println(f"  biggerBits:  0x${biggerBits.toString(16)}")
          println(f"  smallerBits: 0x${smallerBits.toString(16)}")
          println(f"  HW result:  $hwFloat (0x${hwBits.toHexString})")
          println(f"  Reference:  $reference (0x${refBits.toHexString})")
        }

        assert(hwBits == refBits,
          f"Mismatch: $bigger + $smaller\n" +
          f"  HW result:  $hwFloat (0x${hwBits.toHexString})\n" +
          f"  Reference:  $reference (0x${refBits.toHexString})")
      }
    }
  }

  it should "match Scala Float for basic subtraction cases with expDiff=0" in {
    test(new FAddClosePath).withAnnotations(vcdAnnotation) { dut =>
      val testCases = Seq(
        // (bigger, smaller, op, expDiff) - same exponent
        (3.0f, 2.0f, true, 0),       // 3.0 - 2.0 = 1.0 (both exp=128)
        (7.0f, 5.0f, true, 0),       // 7.0 - 5.0 = 2.0 (both exp=129)
        (1.75f, 1.5f, true, 0),      // 1.75 - 1.5 = 0.25 (both exp=127)
        (7.0f, 4.0f, true, 0),       // 7.0 - 4.0 = 3.0 (both exp=129)
        (1.5f, 1.25f, true, 0)       // 1.5 - 1.25 = 0.25 (both exp=127)
      )

      for ((bigger, smaller, isSubtract, expDiff) <- testCases) {
        val biggerBits = floatToBigIntBits(bigger)
        val smallerBits = floatToBigIntBits(smaller)

        dut.io.biggerSrc.poke(biggerBits.U)
        dut.io.smallerSrc.poke(smallerBits.U)
        dut.io.op.poke(isSubtract.B)
        dut.io.expDiff.poke(expDiff.U)

        dut.clock.step(1)

        val hwResult = dut.io.result.peek().litValue
        val hwFloat = bitsToFloat(hwResult)

        val reference = if (isSubtract) bigger - smaller else bigger + smaller

        val hwBits = java.lang.Float.floatToRawIntBits(hwFloat)
        val refBits = java.lang.Float.floatToRawIntBits(reference)

        if (hwBits != refBits) {
          println(f"\n=== FAILED: $bigger - $smaller ===")
          println(f"  biggerBits:  0x${biggerBits.toString(16)}")
          println(f"  smallerBits: 0x${smallerBits.toString(16)}")
          println(f"  HW result:  $hwFloat (0x${hwBits.toHexString})")
          println(f"  Reference:  $reference (0x${refBits.toHexString})")
        }

        assert(hwBits == refBits,
          f"Mismatch: $bigger - $smaller\n" +
          f"  HW result:  $hwFloat (0x${hwBits.toHexString})\n" +
          f"  Reference:  $reference (0x${refBits.toHexString})")
      }
    }
  }

  it should "match Scala Float for basic addition cases with expDiff=1" in {
    test(new FAddClosePath).withAnnotations(vcdAnnotation) { dut =>
      val testCases = Seq(
        // (bigger, smaller, op, expDiff)
        (4.0f, 2.0f, false, 1),      // 4.0 + 2.0 = 6.0
        (6.0f, 3.0f, false, 1),      // 6.0 + 3.0 = 9.0
        (8.0f, 4.0f, false, 1),      // 8.0 + 4.0 = 12.0
        (3.0f, 1.5f, false, 1),      // 3.0 + 1.5 = 4.5
        (5.0f, 2.5f, false, 1)       // 5.0 + 2.5 = 7.5
      )

      for ((bigger, smaller, isSubtract, expDiff) <- testCases) {
        val biggerBits = floatToBigIntBits(bigger)
        val smallerBits = floatToBigIntBits(smaller)

        dut.io.biggerSrc.poke(biggerBits.U)
        dut.io.smallerSrc.poke(smallerBits.U)
        dut.io.op.poke(isSubtract.B)
        dut.io.expDiff.poke(expDiff.U)

        dut.clock.step(1)

        val hwResult = dut.io.result.peek().litValue
        val hwFloat = bitsToFloat(hwResult)

        val reference = if (isSubtract) bigger - smaller else bigger + smaller

        val hwBits = java.lang.Float.floatToRawIntBits(hwFloat)
        val refBits = java.lang.Float.floatToRawIntBits(reference)

        if (hwBits != refBits) {
          println(f"\n=== FAILED: $bigger + $smaller (expDiff=$expDiff) ===")
          println(f"  biggerBits:  0x${biggerBits.toString(16)}")
          println(f"  smallerBits: 0x${smallerBits.toString(16)}")
          println(f"  HW result:  $hwFloat (0x${hwBits.toHexString})")
          println(f"  Reference:  $reference (0x${refBits.toHexString})")
        }

        assert(hwBits == refBits,
          f"Mismatch: $bigger + $smaller\n" +
          f"  HW result:  $hwFloat (0x${hwBits.toHexString})\n" +
          f"  Reference:  $reference (0x${refBits.toHexString})")
      }
    }
  }

  it should "match Scala Float for basic subtraction cases with expDiff=1" in {
    test(new FAddClosePath).withAnnotations(vcdAnnotation) { dut =>
      val testCases = Seq(
        // (bigger, smaller, op, expDiff)
        (6.0f, 3.0f, true, 1),       // 6.0 - 3.0 = 3.0
        (8.0f, 4.0f, true, 1),       // 8.0 - 4.0 = 4.0
        (5.0f, 2.5f, true, 1),       // 5.0 - 2.5 = 2.5
        (3.0f, 1.5f, true, 1),       // 3.0 - 1.5 = 1.5
        (10.0f, 5.0f, true, 1)       // 10.0 - 5.0 = 5.0
      )

      for ((bigger, smaller, isSubtract, expDiff) <- testCases) {
        val biggerBits = floatToBigIntBits(bigger)
        val smallerBits = floatToBigIntBits(smaller)

        dut.io.biggerSrc.poke(biggerBits.U)
        dut.io.smallerSrc.poke(smallerBits.U)
        dut.io.op.poke(isSubtract.B)
        dut.io.expDiff.poke(expDiff.U)

        dut.clock.step(1)

        val hwResult = dut.io.result.peek().litValue
        val hwFloat = bitsToFloat(hwResult)

        val reference = if (isSubtract) bigger - smaller else bigger + smaller

        val hwBits = java.lang.Float.floatToRawIntBits(hwFloat)
        val refBits = java.lang.Float.floatToRawIntBits(reference)

        if (hwBits != refBits) {
          println(f"\n=== FAILED: $bigger - $smaller (expDiff=$expDiff) ===")
          println(f"  biggerBits:  0x${biggerBits.toString(16)}")
          println(f"  smallerBits: 0x${smallerBits.toString(16)}")
          println(f"  HW result:  $hwFloat (0x${hwBits.toHexString})")
          println(f"  Reference:  $reference (0x${refBits.toHexString})")
        }

        assert(hwBits == refBits,
          f"Mismatch: $bigger - $smaller\n" +
          f"  HW result:  $hwFloat (0x${hwBits.toHexString})\n" +
          f"  Reference:  $reference (0x${refBits.toHexString})")
      }
    }
  }

  it should "match Scala Float for mixed sign operations with expDiff=0" in {
    test(new FAddClosePath).withAnnotations(vcdAnnotation) { dut =>
      val testCases = Seq(
        // (bigger, smaller, op, expDiff) - same exponent
        (5.0f, -4.0f, false, 0),     // 5.0 + (-4.0) = 1.0 (both exp=129)
        (-5.0f, 4.0f, false, 0),     // -5.0 + 4.0 = -1.0 (both exp=129)
        (-5.0f, -4.0f, false, 0),    // -5.0 + (-4.0) = -9.0 (both exp=129)
        (3.0f, -2.0f, true, 0)       // 3.0 - (-2.0) = 5.0 (both exp=128)
      )

      for ((bigger, smaller, isSubtract, expDiff) <- testCases) {
        val biggerBits = floatToBigIntBits(bigger)
        val smallerBits = floatToBigIntBits(smaller)

        dut.io.biggerSrc.poke(biggerBits.U)
        dut.io.smallerSrc.poke(smallerBits.U)
        dut.io.op.poke(isSubtract.B)
        dut.io.expDiff.poke(expDiff.U)

        dut.clock.step(1)

        val hwResult = dut.io.result.peek().litValue
        val hwFloat = bitsToFloat(hwResult)

        val reference = if (isSubtract) bigger - smaller else bigger + smaller

        val hwBits = java.lang.Float.floatToRawIntBits(hwFloat)
        val refBits = java.lang.Float.floatToRawIntBits(reference)

        if (hwBits != refBits) {
          println(f"\n=== FAILED: $bigger ${if (isSubtract) "-" else "+"} $smaller (expDiff=$expDiff) ===")
          println(f"  biggerBits:  0x${biggerBits.toString(16)}")
          println(f"  smallerBits: 0x${smallerBits.toString(16)}")
          println(f"  HW result:  $hwFloat (0x${hwBits.toHexString})")
          println(f"  Reference:  $reference (0x${refBits.toHexString})")
        }

        assert(hwBits == refBits,
          f"Mismatch: $bigger ${if (isSubtract) "-" else "+"} $smaller\n" +
          f"  HW result:  $hwFloat (0x${hwBits.toHexString})\n" +
          f"  Reference:  $reference (0x${refBits.toHexString})")
      }
    }
  }

  it should "match Scala Float for mixed sign operations with expDiff=1" in {
    test(new FAddClosePath).withAnnotations(vcdAnnotation) { dut =>
      val testCases = Seq(
        // (bigger, smaller, op, expDiff)
        (8.0f, -4.0f, false, 1),     // 8.0 + (-4.0) = 4.0
        (-8.0f, 4.0f, false, 1),     // -8.0 + 4.0 = -4.0
        (-8.0f, -4.0f, false, 1),    // -8.0 + (-4.0) = -12.0
        (6.0f, -3.0f, true, 1)       // 6.0 - (-3.0) = 9.0
      )

      for ((bigger, smaller, isSubtract, expDiff) <- testCases) {
        val biggerBits = floatToBigIntBits(bigger)
        val smallerBits = floatToBigIntBits(smaller)

        dut.io.biggerSrc.poke(biggerBits.U)
        dut.io.smallerSrc.poke(smallerBits.U)
        dut.io.op.poke(isSubtract.B)
        dut.io.expDiff.poke(expDiff.U)

        dut.clock.step(1)

        val hwResult = dut.io.result.peek().litValue
        val hwFloat = bitsToFloat(hwResult)

        val reference = if (isSubtract) bigger - smaller else bigger + smaller

        val hwBits = java.lang.Float.floatToRawIntBits(hwFloat)
        val refBits = java.lang.Float.floatToRawIntBits(reference)

        if (hwBits != refBits) {
          println(f"\n=== FAILED: $bigger ${if (isSubtract) "-" else "+"} $smaller (expDiff=$expDiff) ===")
          println(f"  biggerBits:  0x${biggerBits.toString(16)}")
          println(f"  smallerBits: 0x${smallerBits.toString(16)}")
          println(f"  HW result:  $hwFloat (0x${hwBits.toHexString})")
          println(f"  Reference:  $reference (0x${refBits.toHexString})")
        }

        assert(hwBits == refBits,
          f"Mismatch: $bigger ${if (isSubtract) "-" else "+"} $smaller\n" +
          f"  HW result:  $hwFloat (0x${hwBits.toHexString})\n" +
          f"  Reference:  $reference (0x${refBits.toHexString})")
      }
    }
  }

  it should "handle edge cases for expDiff=0 subtraction resulting in negative" in {
    test(new FAddClosePath).withAnnotations(vcdAnnotation) { dut =>
      // When expDiff=0 and we subtract, if carry=0, result is negative
      // This tests the absolute value computation path
      val testCases = Seq(
        // Using raw bits to ensure we test smaller - bigger case
        // (biggerSign, biggerExp, biggerMantissa, smallerSign, smallerExp, smallerMantissa, op)
        (0, 130, 0x400000, 0, 130, 0x600000, true),  // smaller mantissa - bigger mantissa
        (0, 128, 0x200000, 0, 128, 0x600000, true),
        (0, 131, 0x000000, 0, 131, 0x400000, true)
      )

      var passCount = 0
      var failCount = 0

      for ((bSign, bExp, bMant, sSign, sExp, sMant, isSubtract) <- testCases) {
        val biggerBits = floatToBits(bSign, bExp, bMant)
        val smallerBits = floatToBits(sSign, sExp, sMant)
        val expDiff = bExp - sExp

        val biggerFloat = bitsToFloat(biggerBits)
        val smallerFloat = bitsToFloat(smallerBits)

        dut.io.biggerSrc.poke(biggerBits.U)
        dut.io.smallerSrc.poke(smallerBits.U)
        dut.io.op.poke(isSubtract.B)
        dut.io.expDiff.poke(expDiff.U)

        dut.clock.step(1)

        val hwResult = dut.io.result.peek().litValue
        val hwFloat = bitsToFloat(hwResult)
        val reference = if (isSubtract) biggerFloat - smallerFloat else biggerFloat + smallerFloat

        val hwBits = java.lang.Float.floatToRawIntBits(hwFloat)
        val refBits = java.lang.Float.floatToRawIntBits(reference)

        if (hwBits == refBits) {
          passCount += 1
        } else {
          failCount += 1
          println(f"\n=== FAILED: $biggerFloat - $smallerFloat ===")
          println(f"  biggerBits:  0x${biggerBits.toString(16)}")
          println(f"  smallerBits: 0x${smallerBits.toString(16)}")
          println(f"  HW result:  $hwFloat (0x${hwBits.toHexString})")
          println(f"  Reference:  $reference (0x${refBits.toHexString})")
        }
      }

      if (failCount > 0) {
        println(f"\nNegative result tests: $passCount passed, $failCount failed")
      }
      assert(failCount == 0, s"$failCount tests failed")
    }
  }

  it should "match Scala Float for random operations with expDiff=0 and expDiff=1" in {
    test(new FAddClosePath){ dut =>
      val rng = new Random(10000)
      var passCount = 0
      var failCount = 0
      val maxFailuresToPrint = 10

      for (i <- 0 until 10000) {
        // Generate two random floats
        val sign1 = rng.nextInt(2)
        val exp1 = rng.nextInt(200) + 20
        val mantissa1 = rng.nextInt(1 << 23)

        val sign2 = rng.nextInt(2)
        val mantissa2 = rng.nextInt(1 << 23)

        val isSubtract = rng.nextBoolean()

        // Test both expDiff=0 and expDiff=1
        val expDiff = rng.nextInt(2)
        val exp2 = exp1 - expDiff

        if (exp1 >= 1 && exp1 <= 254 && exp2 >= 1 && exp2 <= 254) {
          val bits1 = floatToBits(sign1, exp1, mantissa1)
          val bits2 = floatToBits(sign2, exp2, mantissa2)
          val float1 = bitsToFloat(bits1)
          val float2 = bitsToFloat(bits2)

          // For expDiff=0, either order is fine
          // For expDiff=1, bigger has larger exponent
          val (biggerBits, smallerBits, actualExpDiff) = if (exp1 >= exp2) {
            (bits1, bits2, exp1 - exp2)
          } else {
            (bits2, bits1, exp2 - exp1)
          }

          dut.io.biggerSrc.poke(biggerBits.U)
          dut.io.smallerSrc.poke(smallerBits.U)
          dut.io.op.poke(isSubtract.B)
          dut.io.expDiff.poke(actualExpDiff.U)

          dut.clock.step(1)

          val hwResult = dut.io.result.peek().litValue
          val hwFloat = bitsToFloat(hwResult)

          // Calculate reference
          val reference = if (exp1 >= exp2) {
            if (isSubtract) float1 - float2 else float1 + float2
          } else {
            if (isSubtract) float2 - float1 else float2 + float1
          }

          val hwBits = java.lang.Float.floatToRawIntBits(hwFloat)
          val refBits = java.lang.Float.floatToRawIntBits(reference)

          if (hwBits == refBits) {
            passCount += 1
          } else {
            failCount += 1
            if (failCount <= maxFailuresToPrint) {
              println(f"\nTest $i failed:")
              val op = if (isSubtract) "-" else "+"
              if (exp1 >= exp2) {
                println(f"  Operation: $float1 $op $float2")
              } else {
                println(f"  Operation: $float2 $op $float1")
              }
              println(f"  biggerBits:  0x${biggerBits.toString(16)}")
              println(f"  smallerBits: 0x${smallerBits.toString(16)}")
              println(f"  HW result:  $hwFloat (0x${hwBits.toHexString})")
              println(f"  Reference:  $reference (0x${refBits.toHexString})")
              println(f"  expDiff: $actualExpDiff")
            }
          }
        }
      }

      if (failCount > 0) {
        println(f"\nRandom test results: $passCount passed, $failCount failed")
      }
      assert(failCount == 0, s"$failCount out of 10000 random tests failed")
    }
  }
}
