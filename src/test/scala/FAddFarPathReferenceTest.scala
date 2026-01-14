import chisel3._
import chiseltest._
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

class FAddFarPathReferenceTest extends AnyFlatSpec with ChiselScalatestTester {
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

  behavior of "FAddFarPath Reference Model"

  it should "match Scala Float for basic addition cases" in {
    test(new FAddFarPath).withAnnotations(vcdAnnotation) { dut =>
      val testCases = Seq(
        // (bigger, smaller, op, expDiff)
        (2.0f, 0.5f, false, 2),      // 2.0 + 0.5 = 2.5
        (16.0f, 0.125f, false, 7),   // 16.0 + 0.125 = 16.125
        (8.0f, 2.0f, false, 2),      // 8.0 + 2.0 = 10.0
        (4.0f, 1.0f, false, 2),      // 4.0 + 1.0 = 5.0
        (3.0f, 0.75f, false, 2)      // 3.0 + 0.75 = 3.75
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

        assert(hwBits == refBits,
          f"Mismatch: $bigger + $smaller\n" +
          f"  HW result:  $hwFloat (0x${hwBits.toHexString})\n" +
          f"  Reference:  $reference (0x${refBits.toHexString})")
      }
    }
  }

  it should "match Scala Float for subtraction cases" in {
    test(new FAddFarPath).withAnnotations(vcdAnnotation) { dut =>
      val testCases = Seq(
        // (bigger, smaller, op, expDiff)
        (8.0f, 2.0f, true, 2),       // 8.0 - 2.0 = 6.0
        (16.0f, 4.0f, true, 2),      // 16.0 - 4.0 = 12.0
        (5.0f, 1.25f, true, 2)       // 5.0 - 1.25 = 3.75
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

        assert(hwBits == refBits,
          f"Mismatch: $bigger - $smaller\n" +
          f"  HW result:  $hwFloat (0x${hwBits.toHexString})\n" +
          f"  Reference:  $reference (0x${refBits.toHexString})")
      }
    }
  }

  it should "match Scala Float for mixed sign operations" in {
    test(new FAddFarPath).withAnnotations(vcdAnnotation) { dut =>
      val testCases = Seq(
        // (bigger, smaller, op, expDiff)
        (8.0f, -2.0f, false, 2),     // 8.0 + (-2.0) = 6.0
        (-8.0f, 2.0f, false, 2),     // -8.0 + 2.0 = -6.0
        (-8.0f, -2.0f, false, 2),    // -8.0 + (-2.0) = -10.0
        (4.0f, -1.0f, true, 2)       // 4.0 - (-1.0) = 5.0
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

        assert(hwBits == refBits,
          f"Mismatch: $bigger ${if (isSubtract) "-" else "+"} $smaller\n" +
          f"  HW result:  $hwFloat (0x${hwBits.toHexString})\n" +
          f"  Reference:  $reference (0x${refBits.toHexString})")
      }
    }
  }

  // 测试加法溢出规格化（regularPlus1）：结果 >= 2.0，需要右移
  it should "trigger regularPlus1 normalization (addition overflow)" in {
    test(new FAddFarPath).withAnnotations(vcdAnnotation) { dut =>
      // 构造会触发加法溢出的测试用例
      // bigger 尾数接近全1，smaller 对齐后仍能使总和 >= 2.0
      val testCases = Seq(
        // (biggerSign, biggerExp, biggerMantissa, smallerSign, smallerExp, smallerMantissa, op)
        // 1.111...111 + 0.0111...111 >= 2.0
        (0, 130, 0x7FFFFF, 0, 128, 0x7FFFFF, false),  // 尾数全1，expDiff=2
        (0, 130, 0x7FFFFF, 0, 128, 0x7FFFFF, false),  // 同上
        (0, 130, 0x7FFFFE, 0, 128, 0x7FFFFF, false),  // 尾数接近全1
        (0, 130, 0x600000, 0, 128, 0x7FFFFF, false),  // bigger=1.75, smaller对齐后较大
        (0, 131, 0x7FFFFF, 0, 129, 0x7FFFFF, false),  // expDiff=2，尾数全1
        // 负数情况
        (1, 130, 0x7FFFFF, 1, 128, 0x7FFFFF, false),  // 两个负数相加
        // 减去负数 = 加法
        (0, 130, 0x7FFFFF, 1, 128, 0x7FFFFF, true),   // bigger - (-smaller) = 加法
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
          val op = if (isSubtract) "-" else "+"
          println(f"regularPlus1 test failed:")
          println(f"  Operation: $biggerFloat $op $smallerFloat")
          println(f"  HW result:  $hwFloat (0x${hwBits.toHexString})")
          println(f"  Reference:  $reference (0x${refBits.toHexString})")
          println(f"  expDiff: $expDiff")
        }
      }

      println(f"regularPlus1 tests: $passCount passed, $failCount failed")
      assert(failCount == 0, s"$failCount regularPlus1 tests failed")
    }
  }

  // 测试减法下溢规格化（regularMinus1）：结果 < 1.0，需要左移
  // 在 FarPath (expDiff >= 2) 中触发这个需要：bigger 尾数接近 1.0，smaller 对齐后接近 0.5
  it should "trigger regularMinus1 normalization (subtraction underflow)" in {
    test(new FAddFarPath).withAnnotations(vcdAnnotation) { dut =>
      // 构造会触发减法下溢的测试用例
      // bigger = 1.0...，smaller 对齐后接近 0.5，使得 bigger - smaller < 1.0
      val testCases = Seq(
        // (biggerSign, biggerExp, biggerMantissa, smallerSign, smallerExp, smallerMantissa, op)
        // 1.0 - 0.0111...111 ≈ 0.1000...（需要左移）
        (0, 130, 0x000000, 0, 128, 0x7FFFFF, true),   // bigger=1.0，smaller接近2.0，expDiff=2
        (0, 130, 0x000001, 0, 128, 0x7FFFFF, true),   // bigger略大于1.0
        (0, 130, 0x000000, 0, 128, 0x7FFFFE, true),   // smaller略小于2.0
        (0, 131, 0x000000, 0, 129, 0x7FFFFF, true),   // expDiff=2
        // 加上负数 = 减法
        (0, 130, 0x000000, 1, 128, 0x7FFFFF, false),  // bigger + (-smaller) = 减法
        // 负数情况
        (1, 130, 0x000000, 1, 128, 0x7FFFFF, true),   // 负数减负数
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
          val op = if (isSubtract) "-" else "+"
          println(f"regularMinus1 test failed:")
          println(f"  Operation: $biggerFloat $op $smallerFloat")
          println(f"  HW result:  $hwFloat (0x${hwBits.toHexString})")
          println(f"  Reference:  $reference (0x${refBits.toHexString})")
          println(f"  expDiff: $expDiff")
        }
      }

      println(f"regularMinus1 tests: $passCount passed, $failCount failed")
      assert(failCount == 0, s"$failCount regularMinus1 tests failed")
    }
  }

  // 测试舍入后进位（rounding overflow）：舍入+1后产生进位
  it should "trigger rounding overflow" in {
    test(new FAddFarPath).withAnnotations(vcdAnnotation) { dut =>
      // 构造舍入后会产生进位的用例
      // 尾数接近全1，加上舍入后溢出
      val testCases = Seq(
        // bigger 和 smaller 相加后尾数接近全1，舍入时进位
        (0, 130, 0x7FFFFC, 0, 128, 0x000010, false),  // 尾数和接近全1
        (0, 130, 0x7FFFF8, 0, 128, 0x000020, false),
        (0, 130, 0x7FFFF0, 0, 128, 0x000040, false),
        // 不同指数差
        (0, 133, 0x7FFFFC, 0, 130, 0x000010, false),  // expDiff=3
        (0, 135, 0x7FFFFC, 0, 130, 0x000010, false),  // expDiff=5
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
          val op = if (isSubtract) "-" else "+"
          println(f"Rounding overflow test failed:")
          println(f"  Operation: $biggerFloat $op $smallerFloat")
          println(f"  HW result:  $hwFloat (0x${hwBits.toHexString})")
          println(f"  Reference:  $reference (0x${refBits.toHexString})")
          println(f"  expDiff: $expDiff")
        }
      }

      println(f"Rounding overflow tests: $passCount passed, $failCount failed")
      assert(failCount == 0, s"$failCount rounding overflow tests failed")
    }
  }

  // 测试大指数差（sticky位处理）
  it should "handle large exponent differences correctly" in {
    test(new FAddFarPath).withAnnotations(vcdAnnotation) { dut =>
      val testCases = Seq(
        // (biggerSign, biggerExp, biggerMantissa, smallerSign, smallerExp, smallerMantissa, op)
        (0, 150, 0x000000, 0, 118, 0x7FFFFF, false),  // expDiff=32，触发 isLargeShift
        (0, 150, 0x000000, 0, 100, 0x7FFFFF, false),  // expDiff=50
        (0, 200, 0x400000, 0, 130, 0x7FFFFF, false),  // expDiff=70
        (0, 150, 0x000000, 0, 118, 0x7FFFFF, true),   // 减法
        (0, 180, 0x7FFFFF, 0, 140, 0x7FFFFF, false),  // expDiff=40
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
          val op = if (isSubtract) "-" else "+"
          println(f"Large expDiff test failed:")
          println(f"  Operation: $biggerFloat $op $smallerFloat")
          println(f"  HW result:  $hwFloat (0x${hwBits.toHexString})")
          println(f"  Reference:  $reference (0x${refBits.toHexString})")
          println(f"  expDiff: $expDiff")
        }
      }

      println(f"Large expDiff tests: $passCount passed, $failCount failed")
      assert(failCount == 0, s"$failCount large expDiff tests failed")
    }
  }

  it should "match Scala Float for 100000 random operations" in {
    test(new FAddFarPath) { dut =>  // 不输出VCD，避免文件过大
      val rng = new Random(2)
      var passCount = 0
      var failCount = 0

      for (i <- 0 until 100000) {
        // Generate two random floats
        val sign1 = rng.nextInt(2)
        val exp1 = rng.nextInt(200) + 20
        val mantissa1 = rng.nextInt(1 << 23)

        val sign2 = rng.nextInt(2)
        val exp2 = rng.nextInt(200) + 20
        val mantissa2 = rng.nextInt(1 << 23)

        val isSubtract = rng.nextBoolean()

        if (exp1 >= 1 && exp1 <= 254 && exp2 >= 1 && exp2 <= 254 && exp1 != exp2) {
          val bits1 = floatToBits(sign1, exp1, mantissa1)
          val bits2 = floatToBits(sign2, exp2, mantissa2)
          val float1 = bitsToFloat(bits1)
          val float2 = bitsToFloat(bits2)

          // Order by exponent (bigger exponent goes to biggerSrc)
          val (biggerBits, smallerBits, expDiff) = if (exp1 > exp2) {
            (bits1, bits2, exp1 - exp2)
          } else {
            (bits2, bits1, exp2 - exp1)
          }

          // Only test far path cases (expDiff > 1)
          if (expDiff > 1) {
            dut.io.biggerSrc.poke(biggerBits.U)
            dut.io.smallerSrc.poke(smallerBits.U)
            dut.io.op.poke(isSubtract.B)
            dut.io.expDiff.poke(expDiff.U)

            dut.clock.step(1)

            val hwResult = dut.io.result.peek().litValue
            val hwFloat = bitsToFloat(hwResult)

            // Calculate reference - need to account for which operand is which
            val reference = if (exp1 > exp2) {
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
              if (failCount <= 5) { // Print first 5 failures
                println(f"Test $i failed:")
                val op = if (isSubtract) "-" else "+"
                if (exp1 > exp2) {
                  println(f"  Operation: $float1 $op $float2")
                } else {
                  println(f"  Operation: $float2 $op $float1")
                }
                println(f"  HW result:  $hwFloat (0x${hwBits.toHexString})")
                println(f"  Reference:  $reference (0x${refBits.toHexString})")
                println(f"  expDiff: $expDiff")
              }
            }
          }
        }
      }

      println(f"Random test results: $passCount passed, $failCount failed")
      assert(failCount == 0, s"$failCount out of 100000 random tests failed")
    }
  }
}
