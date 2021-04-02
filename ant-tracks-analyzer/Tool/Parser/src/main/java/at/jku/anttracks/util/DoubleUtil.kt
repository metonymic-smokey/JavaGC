package at.jku.anttracks.util

fun Double.format(digits: Int) = java.lang.String.format("%.${digits}f", this)!!
fun Double.toString(format: String): String = String.format(format, this)