package at.jku.anttracks.util

import java.lang.management.ManagementFactory

object Assertion {
    val ASSERTIONS_ENABLED = ManagementFactory.getRuntimeMXBean().inputArguments.contains("-ea")

    inline fun assertion(booleanFun: () -> Boolean) {
        if (ASSERTIONS_ENABLED) {
            assert(booleanFun())
        }
    }

    inline fun assertion(booleanFun: () -> Boolean, messageFun: () -> String) {
        if (ASSERTIONS_ENABLED) {
            assert(booleanFun(), messageFun)
        }
    }
}