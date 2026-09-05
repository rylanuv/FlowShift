package com.flow.shift.feature.exerciseblocker

data class MathProblem(
    val expression: String,
    val answer: Int
)

object MathProblemGenerator {

    fun generate(difficulty: String, problemIndex: Int, seed: Long = System.currentTimeMillis()): MathProblem {
        val random = java.util.Random(seed + problemIndex * 7919L + 31L)
        return when (difficulty.trim().uppercase()) {
            "EASY" -> generateEasy(random)
            "HARD" -> generateHard(random)
            "EXTREME" -> generateExtreme(random)
            else -> generateMedium(random) // Default is Medium
        }
    }

    private fun generateEasy(random: java.util.Random): MathProblem {
        // Simple single-digit to low double-digit addition and subtraction
        val isAddition = random.nextBoolean()
        return if (isAddition) {
            val a = random.nextInt(25) + 5 // 5 to 29
            val b = random.nextInt(20) + 3 // 3 to 22
            MathProblem("$a + $b", a + b)
        } else {
            val a = random.nextInt(35) + 15 // 15 to 49
            val b = random.nextInt(a - 4) + 3 // 3 to a - 2
            MathProblem("$a - $b", a - b)
        }
    }

    private fun generateMedium(random: java.util.Random): MathProblem {
        // Double-digit addition/subtraction, and single-digit times tables
        return when (random.nextInt(3)) {
            0 -> {
                // Addition: double digit
                val a = random.nextInt(60) + 20 // 20 to 79
                val b = random.nextInt(60) + 15 // 15 to 74
                MathProblem("$a + $b", a + b)
            }
            1 -> {
                // Subtraction: double digit
                val a = random.nextInt(60) + 35 // 35 to 94
                val b = random.nextInt(a - 10) + 10 // 10 to a - 1
                MathProblem("$a - $b", a - b)
            }
            else -> {
                // Multiplication: times tables
                val a = random.nextInt(10) + 3 // 3 to 12
                val b = random.nextInt(10) + 3 // 3 to 12
                MathProblem("$a × $b", a * b)
            }
        }
    }

    private fun generateHard(random: java.util.Random): MathProblem {
        // 2-digit multiplication, 3-digit subtraction, or 2-step mixed operation
        return when (random.nextInt(3)) {
            0 -> {
                // 2-digit x 1/2-digit multiplication
                val a = random.nextInt(15) + 12 // 12 to 26
                val b = random.nextInt(9) + 4   // 4 to 12
                MathProblem("$a × $b", a * b)
            }
            1 -> {
                // 3-digit subtraction with borrowing
                val a = random.nextInt(180) + 110 // 110 to 289
                val b = random.nextInt(75) + 35   // 35 to 109
                val larger = maxOf(a, b + 10)
                MathProblem("$larger - $b", larger - b)
            }
            else -> {
                // Mixed operation: a × b + c or a × b - c
                val a = random.nextInt(7) + 6 // 6 to 12
                val b = random.nextInt(7) + 6 // 6 to 12
                val prod = a * b
                val isAdd = random.nextBoolean()
                if (isAdd) {
                    val c = random.nextInt(30) + 10
                    MathProblem("$a × $b + $c", prod + c)
                } else {
                    val c = random.nextInt(prod - 10) + 5
                    MathProblem("$a × $b - $c", prod - c)
                }
            }
        }
    }

    private fun generateExtreme(random: java.util.Random): MathProblem {
        return when (random.nextInt(7)) {
            0 -> {
                // Tough 2-digit x 2-digit multiplication (non-trivial factors)
                var a = random.nextInt(56) + 38 // 38 to 93
                if (a % 10 == 0) a += random.nextInt(8) + 1
                var b = random.nextInt(54) + 27 // 27 to 80
                if (b % 10 == 0) b += random.nextInt(8) + 1
                MathProblem("$a × $b", a * b)
            }
            1 -> {
                // Dual products with subtraction: (a × b) - (c × d)
                val a = random.nextInt(40) + 36 // 36 to 75
                val b = random.nextInt(22) + 18 // 18 to 39
                val c = random.nextInt(18) + 14 // 14 to 31
                val d = random.nextInt(14) + 12 // 12 to 25
                val prod1 = a * b
                val prod2 = c * d
                val larger = maxOf(prod1, prod2)
                val smaller = minOf(prod1, prod2)
                if (prod1 >= prod2) {
                    MathProblem("($a × $b) - ($c × $d)", larger - smaller)
                } else {
                    MathProblem("($c × $d) - ($a × $b)", larger - smaller)
                }
            }
            2 -> {
                // Dual products with addition: (a × b) + (c × d)
                val a = random.nextInt(32) + 28 // 28 to 59
                val b = random.nextInt(24) + 17 // 17 to 40
                val c = random.nextInt(30) + 22 // 22 to 51
                val d = random.nextInt(20) + 15 // 15 to 34
                MathProblem("($a × $b) + ($c × $d)", (a * b) + (c * d))
            }
            3 -> {
                // Difference of squares: a² - b²
                val a = random.nextInt(40) + 42 // 42 to 81
                val b = random.nextInt(22) + 17 // 17 to 38
                MathProblem("$a² - $b²", (a * a) - (b * b))
            }
            4 -> {
                // 3-term nested expression: a × (b + c) - (d × e)
                val a = random.nextInt(18) + 18 // 18 to 35
                val b = random.nextInt(25) + 25 // 25 to 49
                val c = random.nextInt(20) + 20 // 20 to 39
                val d = random.nextInt(11) + 12 // 12 to 22
                val e = random.nextInt(9) + 10  // 10 to 18
                val part1 = a * (b + c)
                val part2 = d * e
                MathProblem("$a × ($b + $c) - ($d × $e)", part1 - part2)
            }
            5 -> {
                // Product combined with exact division: (a × b) ± (c ÷ d)
                val divisor = random.nextInt(11) + 8 // 8 to 18
                val quotient = random.nextInt(40) + 25 // 25 to 64
                val c = divisor * quotient
                val a = random.nextInt(32) + 26 // 26 to 57
                val b = random.nextInt(24) + 16 // 16 to 39
                val prod = a * b
                if (random.nextBoolean() && prod > quotient) {
                    MathProblem("($a × $b) - ($c ÷ $divisor)", prod - quotient)
                } else {
                    MathProblem("($a × $b) + ($c ÷ $divisor)", prod + quotient)
                }
            }
            else -> {
                // Cubes and squares: a³ - b²
                val a = random.nextInt(6) + 9 // 9 to 14 (9³=729 .. 14³=2744)
                val aCube = a * a * a
                val maxB = kotlin.math.sqrt(aCube.toDouble()).toInt() - 6
                val b = random.nextInt(maxOf(1, maxB - 14)) + 14
                MathProblem("$a³ - $b²", aCube - (b * b))
            }
        }
    }
}
