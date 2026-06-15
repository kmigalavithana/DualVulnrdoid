package com.dualvulndroid.dualvulndroid

class SqlInjectionDetector {

    fun detect(code: String): Boolean {

        println("========== CODE ==========")
        println(code)
        println("==========================")

        println("rawQuery = ${code.contains("rawQuery(")}")
        println("SELECT = ${code.contains("SELECT")}")
        println("PLUS = ${code.contains("+")}")

        return code.contains("rawQuery(") &&
                code.contains("SELECT") &&
                code.contains("+")
    }
}