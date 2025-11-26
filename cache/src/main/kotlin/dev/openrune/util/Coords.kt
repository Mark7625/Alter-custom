package dev.openrune.util

fun Coords(x: Int, y: Int, z: Int = 0): Int = (z and 3) shl 28 or ((x and 16383) shl 14) or (y and 16383)