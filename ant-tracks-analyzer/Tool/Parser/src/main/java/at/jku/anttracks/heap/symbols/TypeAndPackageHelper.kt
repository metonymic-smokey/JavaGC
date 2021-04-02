package at.jku.anttracks.heap.symbols

private val nonHighlightPackageStartsInternal =
        arrayOf("Lcom/sun/",
                "Ljava/",
                "Ljavafx/",
                "Ljavassist/",
                "Ljavax/",
                "Ljdk/",
                "Lnet/sourceforge/",
                "Lorg/apache/catalina",
                "Lorg/apache/coyote",
                "Lorg/apache/el",
                "Lorg/apache/ibatis",
                "Lorg/apache/jasper",
                "Lorg/apache/jsp",
                "Lorg/apache/tomcat",
                "Lorg/eclipse/",
                "Lorg/hibernate/",
                "Lorg/hsqldb/",
                "Lorg/mybatis/spring/",
                "Lorg/springframework/",
                "Lorg/terracotta/",
                "Lscala/",
                "Lsun/",
                "VM internal",
                "\$\$Recursion")
private val nonHighlightPackageStartsExternal = nonHighlightPackageStartsInternal.map { it.drop(1).replace('/', '.') }

private val jvmArraysInternal = arrayOf("[Z", "[B", "[S", "[I", "[J", "[F", "[D", "[C")
private val jvmArraysExternal = arrayOf("boolean[]", "byte[]", "short[]", "int[]", "long[]", "float[]", "doube[]", "char[]")
private val jvmArraysSetInternal = jvmArraysInternal.flatMap { arr -> listOf(arr, "[$arr", "[[$arr", "[[[$arr", "[[[[$arr") }.toSet()
private val jvmArraysSetExternal = jvmArraysExternal.flatMap { arr -> listOf(arr, "$arr[]", "$arr[][]", "$arr[][][]", "$arr[][][][]") }.toSet()

val String.internalNameIsPossibleDomainType
    get() = !jvmArraysSetInternal.contains(this) && nonHighlightPackageStartsInternal.none { this.startsWith(it) }

val String.externalNameIsPossibleDomainType
    get() = !jvmArraysSetExternal.contains(this) && nonHighlightPackageStartsExternal.none { this.startsWith(it) }

val AllocationSite.isPossibleDomainType
    get() = this.callSites[0].isPossibleDomainType