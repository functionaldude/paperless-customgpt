package com.functionaldude.paperless_customGPT

fun FloatArray.toDoubleArray(): DoubleArray = map { it.toDouble() }.toDoubleArray()

fun FloatArray.toPgVectorLiteral(): String {
  return joinToString(prefix = "[", postfix = "]") { it.toString() }
}

fun DoubleArray.toPgVectorLiteral(): String {
  return joinToString(prefix = "[", postfix = "]") { it.toString() }
}