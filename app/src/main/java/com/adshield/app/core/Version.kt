package com.adshield.app.core

/**
 * Compares dotted version names such as `1.10.0`.
 *
 * Release tags carry a leading `v`, and a tag may end with a suffix such as `-beta`, so both are
 * tolerated. Comparing segment by segment as numbers is what makes `1.10.0` newer than `1.9.0`,
 * which a plain string comparison gets wrong.
 */
object Version {

    fun compare(left: String, right: String): Int {
        val a = segments(left)
        val b = segments(right)
        for (index in 0 until maxOf(a.size, b.size)) {
            val one = a.getOrElse(index) { 0 }
            val other = b.getOrElse(index) { 0 }
            if (one != other) return if (one > other) 1 else -1
        }
        return 0
    }

    fun isNewer(candidate: String, installed: String): Boolean = compare(candidate, installed) > 0

    private fun segments(value: String): List<Int> = value.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
        .split('.')
        .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
