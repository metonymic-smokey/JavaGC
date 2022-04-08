
package at.jku.anttracks.heap.symbols

import at.jku.anttracks.util.ArraysUtil
import at.jku.anttracks.util.SignatureConverter

open class AllocationSite {
    var id: Int
    val callSites: Array<Location>
    val allocatedTypeId: Int

    /**
     * The original size of the call stack as parsed from the symbols file.
     */
    val stackSizeOriginal: Int

    /**
     * The size of the call stack after static extension.
     */
    val stackSizeExtendedStatic: Int

    class Location(val signature: String,
                   val bci: Int) {

        var shortDeclaringType: String? = null
        var longDeclaringType: String? = null
        var methodName: String? = null
        var shortParameterTypes: Array<String>? = null
        var longParameterTypes: Array<String>? = null
        var shortReturnType: String? = null
        var longReturnType: String? = null
        var isPossibleDomainType = signature.externalNameIsPossibleDomainType

        // VM internal
        lateinit var fullyQualified: String
        // VM internal
        lateinit var omitPackage: String
        // VM internal
        lateinit var shortest: String

        fun resolve() {
            val paramStart = signature.indexOf('(')
            val paramEnd = signature.indexOf(')')

            if (paramStart >= 0 && paramEnd >= 0 && signature.contains(".")) {
                val signatureBeforeParenthisesPart = signature.substring(0, paramStart)
                val declaringTypePart = signatureBeforeParenthisesPart.substring(0, signatureBeforeParenthisesPart.lastIndexOf('.')).replace('/', '.')
                val methodNamePart = signatureBeforeParenthisesPart.substring(declaringTypePart.length + 1)
                val parameterPart = signature.substring(paramStart + 1, paramEnd)
                val returnTypePart = signature.substring(paramEnd + 1)

                if (declaringTypePart.contains("$")) {
                    shortDeclaringType = declaringTypePart.substring(declaringTypePart.substringBefore('$').lastIndexOf('.') + 1).intern()
                } else {
                    shortDeclaringType = declaringTypePart.substring(declaringTypePart.lastIndexOf('.') + 1).intern()
                }
                longDeclaringType = declaringTypePart.intern()
                methodName = methodNamePart
                if (parameterPart.isNotEmpty()) {
                    shortParameterTypes = SignatureConverter.convertToJavaType(parameterPart, true)
                            .split(",".toRegex())
                            .onEach { it.trim() }
                            .dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    for (i in shortParameterTypes!!.indices) {
                        shortParameterTypes!![i] = shortParameterTypes!![i].intern()
                    }
                    longParameterTypes = SignatureConverter.convertToJavaType(parameterPart, false)
                            .split(",".toRegex())
                            .onEach { it.trim() }
                            .dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    for (i in longParameterTypes!!.indices) {
                        longParameterTypes!![i] = longParameterTypes!![i].intern()
                    }
                } else {
                    shortParameterTypes = null
                    longParameterTypes = null
                }

                shortReturnType = SignatureConverter.convertToJavaType(returnTypePart, true).intern()
                longReturnType = SignatureConverter.convertToJavaType(returnTypePart, false).intern()

                var sb = StringBuilder()
                sb.append(longDeclaringType!!)
                sb.append("::")
                sb.append(methodName)
                sb.append("(")
                if (longParameterTypes != null && longParameterTypes!!.isNotEmpty()) {
                    for (i in 0 until longParameterTypes!!.size - 1) {
                        sb.append(longParameterTypes!![i])
                        sb.append(", ")
                    }
                    sb.append(longParameterTypes!![longParameterTypes!!.size - 1])
                }
                sb.append(")")
                // sb.append(" returns ".intern())
                // sb.append(longReturnType!!)
                //sb.append(" : ")
                //sb.append(bci)
                fullyQualified = sb.toString()

                sb = StringBuilder()
                sb.append(shortDeclaringType!!)
                sb.append("::")
                sb.append(methodName)
                sb.append("(")
                if (shortParameterTypes != null && shortParameterTypes!!.isNotEmpty()) {
                    for (i in 0 until shortParameterTypes!!.size - 1) {
                        sb.append(shortParameterTypes!![i])
                        sb.append(", ")
                    }
                    sb.append(shortParameterTypes!![shortParameterTypes!!.size - 1])
                }
                sb.append(")")
                // sb.append(" returns ")
                // sb.append(shortReturnType!!)
                //sb.append(" : ")
                //sb.append(bci)
                omitPackage = sb.toString()

                sb = StringBuilder()
                sb.append(shortDeclaringType!!)
                sb.append("::")
                sb.append(methodName)
                sb.append("()")
                shortest = sb.toString()
            } else {
                // VM internal
                // Fields like parameterTypes should stay null, thus nothing to do
                fullyQualified = signature
                omitPackage = signature
                shortest = signature
            }
        }

        override fun hashCode(): Int {
            val prime = 31
            var result = 1
            result = prime * result + bci
            result = prime * result + (signature.hashCode() ?: 0)
            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null) {
                return false
            }
            if (javaClass != other.javaClass) {
                return false
            }
            val loc = other as Location?
            if (bci != loc!!.bci) {
                return false
            }
            if (signature != loc.signature) {
                return false
            }
            return true
        }

        override fun toString(): String {
            return "$signature:$bci"
        }
    }

    constructor(id: Int, callSites: Array<Location>, allocatedTypeId: Int) {
        this.id = id
        this.callSites = callSites.clone()
        this.allocatedTypeId = allocatedTypeId
        stackSizeOriginal = this.callSites.size
        stackSizeExtendedStatic = stackSizeOriginal
    }

    private constructor(id: Int, callSites: Array<Location>, allocatedTypeId: Int, extendedCallSites: Array<Location>) {
        this.id = id
        this.allocatedTypeId = allocatedTypeId
        this.callSites = ArraysUtil.concat(Location::class.java, callSites, extendedCallSites)
        stackSizeOriginal = callSites.size
        stackSizeExtendedStatic = callSites.size + extendedCallSites.size
    }

    private constructor(original: AllocationSite, newId: Int, callSites: Array<Location>) {
        id = newId
        this.callSites = callSites
        allocatedTypeId = original.allocatedTypeId
        stackSizeOriginal = original.stackSizeOriginal
        stackSizeExtendedStatic = original.stackSizeExtendedStatic
    }

    private constructor(original: AllocationSite, newTypeId: Int) {
        id = original.id
        callSites = original.callSites.clone()
        allocatedTypeId = newTypeId
        stackSizeOriginal = original.stackSizeOriginal
        stackSizeExtendedStatic = original.stackSizeExtendedStatic
    }

    fun copy(newAllocatedTypeId: Int): AllocationSite {
        return AllocationSite(this, newAllocatedTypeId)
    }

    fun extendStatic(additionalCallSites: Array<Location>): AllocationSite {
        return AllocationSite(id, callSites, allocatedTypeId, additionalCallSites)
    }

    fun extendDynamic(newId: Int, extendedCallSites: Array<Location>): AllocationSite {
        val originalId = this.id
        return object : AllocationSite(this@AllocationSite, newId, extendedCallSites) {
            override fun getOriginalID() = originalId
        }
    }

    open fun getOriginalID() = id;

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + allocatedTypeId
        result = prime * result + id
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null) {
            return false
        }
        if (javaClass != other.javaClass) {
            return false
        }
        val other = other as AllocationSite?
        if (allocatedTypeId != other!!.allocatedTypeId) {
            return false
        }
        return if (id != other.id) {
            false
        } else true
    }

    override fun toString(): String {
        val string = StringBuilder()
        for (location in callSites) {
            string.append(location.toString() + "\n")
        }
        return string.toString().trim { it <= ' ' }
    }

    companion object {
        const val MAGIC_BYTE = 42
        const val MAGIC_BYTE_SIMPLE = 84
        const val ALLOCATION_SITE_IDENTIFIER_UNKNOWN = 0
        const val UNKNOWN_BCI = -1
    }
}
