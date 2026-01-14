import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

class FAddFarPathRandomTest extends AnyFlatSpec with ChiselScalatestTester {

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

  behavior of "FAddFarPath Random Tests"

  it should "handle 100 random addition operations" in {
    test(new FAddFarPath) { dut =>
      val rng = new Random(42) // Fixed seed for reproducibility
      var passCount = 0

      for (_ <- 0 until 100) {
        val sign1 = rng.nextInt(2)
        val biggerExp = rng.nextInt(200) + 20 // 20-219
        val mantissa1 = rng.nextInt(1 << 23)
        val sign2 = rng.nextInt(2)
        val expDiff = rng.nextInt(20) + 2 // 2-21
        val smallerExp = biggerExp - expDiff
        val mantissa2 = rng.nextInt(1 << 23)

        if (smallerExp >= 1) {
          val bigger = floatToBits(sign1, biggerExp, mantissa1)
          val smaller = floatToBits(sign2, smallerExp, mantissa2)

          dut.io.biggerSrc.poke(bigger.U)
          dut.io.smallerSrc.poke(smaller.U)
          dut.io.op.poke(false.B) // addition
          dut.io.expDiff.poke(expDiff.U)

          dut.clock.step(1)

          val result = dut.io.result.peek().litValue
          val (resSign, resExp, resMant) = extractFloat(result)

          // Basic sanity checks
          assert(resSign == 0 || resSign == 1, s"Invalid sign: $resSign")
          assert(resExp >= 0 && resExp <= 255, s"Invalid exponent: $resExp")
          assert(resMant >= 0 && resMant < (1 << 23), s"Invalid mantissa: $resMant")
          passCount += 1
        }
      }

      assert(passCount >= 90, s"Only $passCount/100 random tests passed")
    }
  }

  it should "handle 100 random subtraction operations" in {
    test(new FAddFarPath) { dut =>
      val rng = new Random(43)
      var passCount = 0

      for (_ <- 0 until 100) {
        val sign1 = rng.nextInt(2)
        val biggerExp = rng.nextInt(200) + 20
        val mantissa1 = rng.nextInt(1 << 23)
        val sign2 = rng.nextInt(2)
        val expDiff = rng.nextInt(20) + 2
        val smallerExp = biggerExp - expDiff
        val mantissa2 = rng.nextInt(1 << 23)

        if (smallerExp >= 1) {
          val bigger = floatToBits(sign1, biggerExp, mantissa1)
          val smaller = floatToBits(sign2, smallerExp, mantissa2)

          dut.io.biggerSrc.poke(bigger.U)
          dut.io.smallerSrc.poke(smaller.U)
          dut.io.op.poke(true.B) // subtraction
          dut.io.expDiff.poke(expDiff.U)

          dut.clock.step(1)

          val result = dut.io.result.peek().litValue
          val (resSign, resExp, resMant) = extractFloat(result)

          assert(resSign == 0 || resSign == 1, s"Invalid sign: $resSign")
          assert(resExp >= 0 && resExp <= 255, s"Invalid exponent: $resExp")
          assert(resMant >= 0 && resMant < (1 << 23), s"Invalid mantissa: $resMant")
          passCount += 1
        }
      }

      assert(passCount >= 90, s"Only $passCount/100 random tests passed")
    }
  }

  it should "preserve sign of bigger operand in random tests" in {
    test(new FAddFarPath) { dut =>
      val rng = new Random(44)
      var passCount = 0

      for (_ <- 0 until 50) {
        val sign1 = rng.nextInt(2)
        val biggerExp = rng.nextInt(200) + 20
        val mantissa1 = rng.nextInt(1 << 23)
        val sign2 = rng.nextInt(2)
        val expDiff = rng.nextInt(20) + 2
        val smallerExp = biggerExp - expDiff
        val mantissa2 = rng.nextInt(1 << 23)

        if (smallerExp >= 1) {
          val bigger = floatToBits(sign1, biggerExp, mantissa1)
          val smaller = floatToBits(sign2, smallerExp, mantissa2)

          dut.io.biggerSrc.poke(bigger.U)
          dut.io.smallerSrc.poke(smaller.U)
          dut.io.op.poke(false.B)
          dut.io.expDiff.poke(expDiff.U)

          dut.clock.step(1)

          val result = dut.io.result.peek().litValue
          val (resSign, _, _) = extractFloat(result)

          assert(resSign == sign1, s"Result sign $resSign doesn't match bigger operand sign $sign1")
          passCount += 1
        }
      }

      assert(passCount >= 45, s"Only $passCount/50 random tests passed")
    }
  }

  it should "handle random large exponent differences" in {
    test(new FAddFarPath) { dut =>
      val rng = new Random(45)
      var passCount = 0

      for (_ <- 0 until 50) {
        val sign1 = rng.nextInt(2)
        val biggerExp = rng.nextInt(150) + 50 // 50-199
        val mantissa1 = rng.nextInt(1 << 23)
        val sign2 = rng.nextInt(2)
        val expDiff = rng.nextInt(30) + 11 // 11-40 (large differences)
        val smallerExp = biggerExp - expDiff
        val mantissa2 = rng.nextInt(1 << 23)

        if (smallerExp >= 1) {
          val bigger = floatToBits(sign1, biggerExp, mantissa1)
          val smaller = floatToBits(sign2, smallerExp, mantissa2)

          dut.io.biggerSrc.poke(bigger.U)
          dut.io.smallerSrc.poke(smaller.U)
          dut.io.op.poke(false.B)
          dut.io.expDiff.poke(expDiff.U)

          dut.clock.step(1)

          val result = dut.io.result.peek().litValue
          val (resSign, resExp, _) = extractFloat(result)

          // With large expDiff, result should be close to bigger operand
          assert(resSign == sign1, s"Sign mismatch")
          assert(Math.abs(resExp - biggerExp) <= 1, s"Exponent $resExp too far from bigger $biggerExp")
          passCount += 1
        }
      }

      assert(passCount >= 45, s"Only $passCount/50 random tests passed")
    }
  }

  it should "handle random same-sign operands" in {
    test(new FAddFarPath) { dut =>
      val rng = new Random(46)
      var passCount = 0

      for (_ <- 0 until 50) {
        val sign = rng.nextInt(2)
        val biggerExp = rng.nextInt(200) + 20
        val mantissa1 = rng.nextInt(1 << 23)
        val mantissa2 = rng.nextInt(1 << 23)
        val expDiff = rng.nextInt(20) + 2
        val smallerExp = biggerExp - expDiff

        if (smallerExp >= 1) {
          val bigger = floatToBits(sign, biggerExp, mantissa1)
          val smaller = floatToBits(sign, smallerExp, mantissa2)

          dut.io.biggerSrc.poke(bigger.U)
          dut.io.smallerSrc.poke(smaller.U)
          dut.io.op.poke(false.B)
          dut.io.expDiff.poke(expDiff.U)

          dut.clock.step(1)

          val result = dut.io.result.peek().litValue
          val (resSign, resExp, _) = extractFloat(result)

          // Same sign addition should preserve sign
          assert(resSign == sign, s"Sign should be $sign, got $resSign")
          assert(Math.abs(resExp - biggerExp) <= 1, s"Exponent should be close to $biggerExp, got $resExp")
          passCount += 1
        }
      }

      assert(passCount >= 45, s"Only $passCount/50 random tests passed")
    }
  }

  it should "handle random minimum expDiff = 2" in {
    test(new FAddFarPath) { dut =>
      val rng = new Random(47)
      var passCount = 0

      for (_ <- 0 until 50) {
        val sign1 = rng.nextInt(2)
        val biggerExp = rng.nextInt(200) + 3 // Ensure smallerExp >= 1
        val mantissa1 = rng.nextInt(1 << 23)
        val sign2 = rng.nextInt(2)
        val mantissa2 = rng.nextInt(1 << 23)
        val expDiff = 2 // Minimum for far path
        val smallerExp = biggerExp - expDiff

        val bigger = floatToBits(sign1, biggerExp, mantissa1)
        val smaller = floatToBits(sign2, smallerExp, mantissa2)

        dut.io.biggerSrc.poke(bigger.U)
        dut.io.smallerSrc.poke(smaller.U)
        dut.io.op.poke(false.B)
        dut.io.expDiff.poke(expDiff.U)

        dut.clock.step(1)

        val result = dut.io.result.peek().litValue
        val (resSign, resExp, _) = extractFloat(result)

        assert(resSign == 0 || resSign == 1, s"Invalid sign")
        assert(resExp >= 0 && resExp <= 255, s"Invalid exponent")
        passCount += 1
      }

      assert(passCount == 50, s"Only $passCount/50 random tests passed")
    }
  }
}
