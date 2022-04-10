

package at.jku.anttracks.callcontext;

import at.jku.anttracks.callcontext.cfg.Block;
import at.jku.anttracks.callcontext.cfg.ControlFlow;
import at.jku.anttracks.callcontext.util.SetMultimap;
import javassist.*;
import javassist.bytecode.*;
import javassist.bytecode.BootstrapMethodsAttribute.BootstrapMethod;
import javassist.bytecode.analysis.Analyzer;
import javassist.bytecode.analysis.Frame;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.invoke.LambdaMetafactory;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static javassist.Modifier.*;
import static javassist.bytecode.Descriptor.toJavaName;

/**
 * This class must be used to build a {@link CallContextAnalyzer}. It analyzes classes loaded by the JVM for context information (e.g.
 * allocations immediately before and after calls) and builds a {@code CallContextAnalyzer}. Note that this class is not thread-safe (as
 * opposed to the built {@code CallContextAnalyzer}).
 * <p>
 * First, classes are added by calling {@link #addClass(byte[])} with the contents of the class file. Classes should be added in the same
 * order as they are loaded by the JVM. After all classes have been added, {@link #build()} must be called to obtain the
 * {@code CallContextAnalyzer} instance.
 *
 * @author Peter Feichtinger
 */
public class CallContextBuilder {

    static class Counters {
        /**
         * The number of classes added.
         */
        public int classes;
        /**
         * The number of methods parsed.
         */
        public int methods;
        /**
         * Number of methods that were skipped because of unsupported instructions.
         */
        public int skippedMethods;
        /**
         * Number of dynamic calls that were skipped.
         */
        public int unhandledDynamicCalls;
        /**
         * Number of methods where the target class was found, but the target method was not found in that class.
         */
        public int missingMethods;
        /**
         * The number of classes added more than once.
         */
        public int redefinedClasses;
        /**
         * Number of duplicate call sites (happens when classes are redefined).
         */
        public int duplicateCallSites;
        /**
         * Number of call targets for which a more derived type could be determined using type analysis.
         */
        public int specializedCallTargets;

        public void log(Logger logger) {
            logger.info(() -> String.format("Successfully parsed %,d classes with %,d methods.", classes, methods));
            if (specializedCallTargets > 0) {
                logger.info(() -> String.format("Target class of %d call sites specialized from type analysis.", specializedCallTargets));
            }
            if (skippedMethods > 0) {
                logger.warning(String.format("Skipped %,d methods because of unsupported instructions.", skippedMethods));
            }
            if (unhandledDynamicCalls > 0) {
                logger.warning(String.format("There were %,d unhandled dynamic calls.", unhandledDynamicCalls));
            }
            if (missingMethods > 0) {
                logger.warning(String.format("%,d target methods were not found in their classes.", missingMethods));
            }
            if (redefinedClasses > 0) {
                final String msg = "There were %,d redefined classes (classes which were added more than once).";
                logger.warning(String.format(msg, redefinedClasses));
            }
            if (duplicateCallSites > 0) {
                final String msg = "There were %,d duplicate call sites (most likely caused by redefined classes).";
                logger.warning(String.format(msg, duplicateCallSites));
            }
        }
    }

    /**
     * Thrown by {@link CallContextBuilder#parseMethod(ClassFile, MethodInfo)} to indicate that the class file is missing an attribute.
     *
     * @author Peter Feichtinger
     */
    static class MissingAttributeException extends BadBytecode {
        private static final long serialVersionUID = -3425479950507770083L;

        /**
         * Create a new {@link MissingAttributeException} with the specified attribute name.
         *
         * @param attributeName The name of the missing attribute.
         */
        public MissingAttributeException(String attributeName) {
            super("Class is missing attribute " + attributeName);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CallContextBuilder.class.getSimpleName());
    private static final char[] ATYPE_DESCRIPTORS = {'Z', 'C', 'F', 'D', 'B', 'S', 'I', 'J'};
    private static final int ATYPE_MIN = Opcode.T_BOOLEAN;
    private static final int ATYPE_MAX = Opcode.T_LONG;
    private static final String CORRUPT_CONST_POOL_MSG_FORMAT = "Constant pool of class %s seems to be corrupt: %s";
    /**
     * {@link Predicate} for testing whether a {@link CtMethod} can override a superclass method, or be overridden by a subclass method.
     * Returns {@code true} when the subject is neither <i>private</i> nor <i>static</i>.
     */
    private static final Predicate<CtMethod> PRED_CAN_OVERRIDE = m -> !isPrivate(m.getModifiers()) && !isStatic(m.getModifiers());

    // Bootstrap methods handled by #resolveDynamicCallTarget
    /**
     * {@linkplain MethodName Method name} of
     * {@link LambdaMetafactory#metafactory(java.lang.invoke.MethodHandles.Lookup, String, java.lang.invoke.MethodType, java.lang.invoke
     * .MethodType, java
     * .lang.invoke
     * .MethodHandle, java.lang.invoke.MethodType)
     * LambdaMetafactory::metafactory}.
     */
    private static final MethodName BOOTSTRAP_METAFACTORY = new MethodName("java.lang.invoke.LambdaMetafactory",
                                                                           "metafactory",
                                                                           "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;" +
                                                                                   "Ljava/lang/invoke/MethodType;" +
                                                                                   "Ljava/lang/invoke/MethodType;" + "Ljava/lang/invoke/MethodHandle;" +
                                                                                   "Ljava/lang/invoke/MethodType;)" + "Ljava/lang/invoke/CallSite;",
                                                                           null);
    /**
     * {@linkplain MethodName Method name} of
     * {@link LambdaMetafactory#altMetafactory(java.lang.invoke.MethodHandles.Lookup, String, java.lang.invoke.MethodType, Object...)
     * LambdaMetafactory::altMetafactory}.
     */
    private static final MethodName BOOTSTRAP_ALTMETAFACTORY = new MethodName("java.lang.invoke.LambdaMetafactory",
                                                                              "altMetafactory",
                                                                              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;" +
                                                                                      "Ljava/lang/invoke/MethodType;" +
                                                                                      "[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                                                                              null);

    /**
     * The {@link CallContextAnalyzer} instance being built.
     */
    private final CallContextAnalyzer mIt;
    private boolean mEnableTypeAnalysis;

    private final ClassPool mClassPool = new ClassPool();
    private final Set<CtClass> mClasses = new HashSet<>();
    private final SetMultimap<CtClass, CtClass> mSuperclasses = new SetMultimap<>();
    /**
     * Maps class names to call sites with targets in that class.
     */
    private final SetMultimap<String, CallSite> mPendingTargets = new SetMultimap<>();
    /**
     * Maps call sites caused by {@code newInvokeSpecial} method handles to the allocated type name.
     */
    private final Map<CallSite, String> mNewInvokespecialAllocations = new HashMap<>();
    private final Counters mCounters = new Counters();

    private final Cache<String> cache = new Cache<>();

    /**
     * Create a new {@link CallContextBuilder}.
     */
    CallContextBuilder() {
        mIt = new CallContextAnalyzer();
        mEnableTypeAnalysis = true;
    }

    /**
     * Set whether to perform data flow analysis to specialize target types of method calls. This is enabled by default. This method can be
     * called any time before {@link #build()} is called.
     *
     * @param enable Whether to enable type analysis when {@link #build()} is called.
     * @return This instance for convenience.
     */
    public CallContextBuilder enableTypeAnalysis(boolean enable) {
        mEnableTypeAnalysis = enable;
        return this;
    }

    /**
     * Add a class to the analyzer from the specified class definition.
     *
     * @param data The contents of a class file as a byte array.
     * @return This instance for convenience.
     * @throws BadClassDefinitionException in case the class file could not be parsed or contains invalid bytecode. The state of the
     *                                     analyzer is not changed
     *                                     if this
     *                                     exception gets thrown (i.e. this method provides strong exception safety).
     */
    public CallContextBuilder addClass(byte[] data) {
        final ClassFile classFile = makeClassFile(data);
        @SuppressWarnings("unchecked")
        final List<MethodInfo> methods = classFile.getMethods();
        final List<CallSite> callSites = new ArrayList<>();
        for (MethodInfo method : methods) {
            try {
                callSites.addAll(parseMethod(classFile, method));
                mCounters.methods++;
            } catch (BadBytecode ex) {
                final String msg = "Method " + makeMethodName(classFile, method, cache) + " contains invalid bytecode.";
                throw new at.jku.anttracks.callcontext.BadClassDefinitionException(data, msg, ex);
            }
        }

        // Only add to the class pool if parsing methods was successful
        final CtClass clazz = addNewClass(classFile);
        addCalls(callSites);
        // Analyze basic blocks in all methods for unambiguous allocations before and after calls
        Arrays.stream(clazz.getDeclaredBehaviors()).forEach(this::analyzeControlFlow);

        return this;
    }

    /**
     * Add all class definitions in the specified stream.
     *
     * @param classes A {@link Stream} of class definitions, the stream is consumed.
     * @return This instance for convenience.
     * @throws BadClassDefinitionException in case a class file could not be parsed or contains invalid bytecode.
     * @see #addClass(byte[])
     */
    public CallContextBuilder addClasses(Stream<byte[]> classes) {
        classes.forEach(this::addClass);
        return this;
    }

    /**
     * Finish building the {@link CallContextAnalyzer} and get the instance.
     *
     * @return The built {@code CallContextAnalyzer} instance.
     */
    public CallContextAnalyzer build() {
        LOGGER.info("Building call context analyzer.");
        mNewInvokespecialAllocations.forEach((cs, type) -> cs.addAllocationBefore(cache.get(type)));

        if (mEnableTypeAnalysis) {
            // Perform type analysis (need to have all classes loaded for it to work)
            mClasses.stream().flatMap(c -> Arrays.stream(c.getDeclaredBehaviors())).forEach(this::analyzeDataFlow);
        }

        mCounters.log(LOGGER);
        LOGGER.info(() -> {
            final long count = mIt.stream().filter(cs -> cs.getAllocationsAfter().size() + cs.getAllocationsBefore().size() > 0).count();
            final int allocs = mIt.stream().mapToInt(cs -> cs.getAllocationsAfter().size() + cs.getAllocationsBefore().size()).sum();
            return String.format("There are %,d allocations associated with %,d out of %,d call sites.", allocs, count, mIt.size());
        });
        if (!mPendingTargets.isEmpty()) {
            LOGGER.info(() -> {
                final String msg = "There are %,d unresolved call targets in %,d classes.";
                return String.format(msg, mPendingTargets.size(), mPendingTargets.keySet().size());
            });
        }
        return mIt;
    }

    /**
     * Parse the specified method for calls.
     *
     * @param clazz  The class the method belongs to.
     * @param method The method to process.
     * @return A list of {@linkplain CallSite call sites} found in the method.
     * @throws BadBytecode               in case the class file contains invalid bytecode.
     * @throws MissingAttributeException If the class file has no {@code BootstrapMethods} attribute and {@code method} contains an
     *                                   {@code invokedynamic}
     *                                   instruction.
     */
    private List<CallSite> parseMethod(ClassFile clazz, MethodInfo method) throws BadBytecode {
        final MethodName callerName = makeMethodName(clazz, method, cache);
        final CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            // Method has no code attribute (e.g. native method).
            return Collections.emptyList();
        }
        final ConstPool cp = method.getConstPool();
        final BootstrapMethodsAttribute bootstrapMethods = (BootstrapMethodsAttribute) clazz.getAttribute(BootstrapMethodsAttribute.tag);
        final List<CallSite> result = new ArrayList<>();

        for (CodeIterator it = code.iterator(); it.hasNext(); ) {
            final int opIdx = it.next();
            final int op = it.byteAt(opIdx);
            int cpIdx;
            final String className;
            final String methodName;
            final String descriptor;
            final MethodName target;
            switch (op) {
                case Opcode.INVOKEDYNAMIC:
                    if (bootstrapMethods == null) {
                        throw new MissingAttributeException(BootstrapMethodsAttribute.tag);
                    }
                    target = resolveDynamicCallTarget(it.u16bitAt(opIdx + 1), cp, bootstrapMethods);
                    if (target != null) {
                        LOGGER.finest(() -> String.format("Dynamic call resolved to %s at %s", target, CallSite.makeName(callerName, opIdx)));
                        final CallSite cs = new CallSite(callerName, opIdx, target);
                        if (target.isConstructor()) {
                            mNewInvokespecialAllocations.put(cs, target.className);
                        }
                        result.add(cs);
                    } else {
                        LOGGER.fine(() -> "Dynamic method call not handled at " + CallSite.makeName(callerName, opIdx));
                        mCounters.unhandledDynamicCalls++;
                    }
                    continue;

                case Opcode.INVOKEINTERFACE:
                    cpIdx = it.u16bitAt(opIdx + 1);
                    className = cp.getInterfaceMethodrefClassName(cpIdx);
                    methodName = cp.getInterfaceMethodrefName(cpIdx);
                    descriptor = cp.getInterfaceMethodrefType(cpIdx);
                    break;

                case Opcode.INVOKESPECIAL:
                case Opcode.INVOKESTATIC:
                case Opcode.INVOKEVIRTUAL:
                    cpIdx = it.u16bitAt(opIdx + 1);
                    className = cp.getMethodrefClassName(cpIdx);
                    methodName = cp.getMethodrefName(cpIdx);
                    descriptor = cp.getMethodrefType(cpIdx);
                    break;

                default:
                    continue;
            }
            target = new MethodName(className, methodName, descriptor, cache);
            result.add(new CallSite(callerName, opIdx, target));
        }
        return result;
    }

    /**
     * Try to resolve the call target of a dynamic call (an <i>invokedynamic</i> instruction).
     *
     * @param cpIdx     The constant pool index of the call site specifier ({@code InvokeDynamic_info} structure).
     * @param cp        The constant pool.
     * @param bsMethods The {@link BootstrapMethodsAttribute} of the class containing the dynamic call.
     * @return Either a {@link MethodName} for the target method, or {@code null} if a unique target method cannot be determined statically.
     */
    private static MethodName resolveDynamicCallTarget(int cpIdx, ConstPool cp, BootstrapMethodsAttribute bsMethods) {
        final BootstrapMethod bsMethod = bsMethods.getMethods()[cp.getInvokeDynamicBootstrap(cpIdx)];
        if (cp.getMethodHandleKind(bsMethod.methodRef) != ConstPool.REF_invokeStatic) {
            // This method only handles static bootstrap methods
            return null;
        }
        final int bsHandleIdx = cp.getMethodHandleIndex(bsMethod.methodRef);
        if (cp.getTag(bsHandleIdx) != ConstPool.CONST_Methodref) {
            LOGGER.warning(String.format(CORRUPT_CONST_POOL_MSG_FORMAT, cp.getClassName(), "bootstrap method does not refer to a method reference."));
            return null;
        }

        final String bsMethodClass = cp.getMethodrefClassName(bsHandleIdx);
        final String bsMethodName = cp.getMethodrefName(bsHandleIdx);
        final String bsMethodType = cp.getMethodrefType(bsHandleIdx);
        if (BOOTSTRAP_METAFACTORY.equals(bsMethodClass, bsMethodName, bsMethodType)) {
            // The second static argument to the bootstrap method is a handle to the called method, or to a constructor
            if (bsMethod.arguments.length != 3 || cp.getTag(bsMethod.arguments[1]) != ConstPool.CONST_MethodHandle) {
                // Unexpected argument count or argument type, bail out
                LOGGER.warning(String.format(CORRUPT_CONST_POOL_MSG_FORMAT,
                                             cp.getClassName(),
                                             "second static argument to LambdaMetafactory.metafactory is not a method handle."));
                return null;
            }
            return getNameForLambdaMetafactory(cp, bsMethod.arguments[1]);

        } else if (BOOTSTRAP_ALTMETAFACTORY.equals(bsMethodClass, bsMethodName, bsMethodType)) {
            // The second static argument to the bootstrap method is a handle to the called method, or to a constructor
            if (bsMethod.arguments.length < 3 || cp.getTag(bsMethod.arguments[1]) != ConstPool.CONST_MethodHandle) {
                // Unexpected argument count or argument type, bail out
                LOGGER.warning(String.format(CORRUPT_CONST_POOL_MSG_FORMAT,
                                             cp.getClassName(),
                                             "second static argument to LambdaMetafactory.altMetafactory is not a method handle."));
                return null;
            }
            if (bsMethod.arguments.length > 3) {
                final int argFlagsIdx = bsMethod.arguments[3];
                if (cp.getTag(argFlagsIdx) != ConstPool.CONST_Integer) {
                    LOGGER.warning(String.format(CORRUPT_CONST_POOL_MSG_FORMAT,
                                                 cp.getClassName(),
                                                 "4th static argument to LambdaMetafactory.altMetafactory is not an integer."));
                    return null;
                }
                final int flags = cp.getIntegerInfo(argFlagsIdx);
                int argIdx = 4;
                if ((flags & LambdaMetafactory.FLAG_MARKERS) != 0) {
                    final int argMarkerCountIdx = bsMethod.arguments[argIdx++];
                    if (cp.getTag(argMarkerCountIdx) != ConstPool.CONST_Integer) {
                        LOGGER.warning(String.format(CORRUPT_CONST_POOL_MSG_FORMAT,
                                                     cp.getClassName(),
                                                     "marker count static argument to LambdaMetafactory.altMetafactory is not an integer" + ""));
                        return null;
                    }
                    final int markerCount = cp.getIntegerInfo(argMarkerCountIdx);
                    argIdx += markerCount;
                }
                if ((flags & LambdaMetafactory.FLAG_BRIDGES) != 0) {
                    final int argBridgeCountIdx = bsMethod.arguments[argIdx];
                    if (cp.getTag(argBridgeCountIdx) != ConstPool.CONST_Integer) {
                        LOGGER.warning(String.format(CORRUPT_CONST_POOL_MSG_FORMAT,
                                                     cp.getClassName(),
                                                     "bridge count static argument to LambdaMetafactory.altMetafactory is not an integer" + ""));
                        return null;
                    }
                    final int bridgeCount = cp.getIntegerInfo(argBridgeCountIdx);
                    if (bridgeCount != 0) {
                        // This method only handles simple lambdas without additional bridge methods
                        return null;
                    }
                }
            } // if(bsMethod.arguments.length > 3)
            return getNameForLambdaMetafactory(cp, bsMethod.arguments[1]);
        }

        // Unhandled bootstrap method
        return null;
    }

    /**
     * Get a method name for the specified static argument to one of the {@link LambdaMetafactory} bootstrap methods.
     *
     * @param cp           The constant pool to use.
     * @param argHandleIdx The index of the method handle that represents the target method of the dynamic call.
     * @return A {@link MethodName} for the target, or {@code null}.
     */
    private static MethodName getNameForLambdaMetafactory(ConstPool cp, int argHandleIdx) {
        assert cp.getTag(argHandleIdx) == ConstPool.CONST_MethodHandle;
        if (inRange(cp.getMethodHandleKind(argHandleIdx), ConstPool.REF_invokeVirtual, ConstPool.REF_invokeInterface)) {
            final int targetHandleIdx = cp.getMethodHandleIndex(argHandleIdx);
            final String targetClass;
            final String targetName;
            final String targetType;
            switch (cp.getTag(targetHandleIdx)) {
                case ConstPool.CONST_Methodref:
                    targetClass = cp.getMethodrefClassName(targetHandleIdx);
                    targetName = cp.getMethodrefName(targetHandleIdx);
                    targetType = cp.getMethodrefType(targetHandleIdx);
                    break;
                case ConstPool.CONST_InterfaceMethodref:
                    targetClass = cp.getInterfaceMethodrefClassName(targetHandleIdx);
                    targetName = cp.getInterfaceMethodrefName(targetHandleIdx);
                    targetType = cp.getInterfaceMethodrefType(targetHandleIdx);
                    break;
                default:
                    LOGGER.warning(String.format(CORRUPT_CONST_POOL_MSG_FORMAT, cp.getClassName(), "target method handle does not refer to a method."));
                    return null;
            }
            return new MethodName(targetClass, targetName, targetType, null);
        }
        // Unexpected type of method handle (can you even putfield in a lambda?)
        return null;
    }

    /**
     * Handle a new class after it has been parsed successfully, but before its calls are added.
     *
     * @param classFile The class that was added.
     * @return The {@link CtClass} that was added.
     */
    private CtClass addNewClass(ClassFile classFile) {
        if (mClassPool.getOrNull(classFile.getName()) != null) {
            LOGGER.fine(() -> "Redefining class " + classFile.getName());
            mCounters.redefinedClasses++;
        }
        mCounters.classes++;
        final CtClass clazz = mClassPool.makeClass(classFile);
        final Set<CallSite> targets = mPendingTargets.removeAll(clazz.getName());
        if (targets != null) {
            targets.stream().forEach(call -> checkAbstractTarget(clazz, call));
        }
        try {
            // Find superclass methods for which methods in the new class might be dynamically bound targets
            final List<MethodName> supers = getAllSupers(clazz).stream()
                                                               .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
                                                               .filter(PRED_CAN_OVERRIDE)
                                                               .map(m -> MethodName.create(m, cache))
                                                               .collect(Collectors.toList());
            Arrays.stream(clazz.getDeclaredMethods()).filter(PRED_CAN_OVERRIDE).map(m -> MethodName.create(m, cache)).forEach(newName -> {
                // There is no parameter contravariance in Java, so MethodName::signatureIdentical is sufficient
                supers.stream().filter(newName::signatureIdentical).forEach(superName -> mIt.putOverride(superName, newName));
            });
        } catch (NotFoundException ex) {
            LOGGER.log(Level.WARNING, ex, () -> {
                final String msg = "Failed to get supers for class %s%n" + " -> Dynamically bound calls to overriding methods in this class will not be resolved " +
                        "properly.";
                return String.format(msg, clazz.getName());
            });
        }
        mClasses.add(clazz);
        return clazz;
    }

    /**
     * Add the specified calls to the graph.
     *
     * @param callSites The call sites to add.
     */
    private void addCalls(List<CallSite> callSites) {
        for (CallSite call : callSites) {
            if (!mIt.addCall(call)) {
                LOGGER.finest(() -> "Ignored duplicate call site: " + call);
                mCounters.duplicateCallSites++;
                continue;
            }
            final CtClass targetClass = mClassPool.getOrNull(call.getTarget().className);
            if (targetClass != null) {
                checkAbstractTarget(targetClass, call);
            } else {
                mPendingTargets.put(call.getTarget().className, call);
            }
        } // for(CallSite : callSites)
    }

    /**
     * Check whether a call is to an abstract target method and add it to the map of abstract targets.
     *
     * @param targetClass The class that contains the target method.
     * @param call        The call site.
     */
    private void checkAbstractTarget(CtClass targetClass, CallSite call) {
        assert targetClass != null;
        final MethodName target = call.getTarget();
        // Constructors are never abstract
        if (!target.isConstructor()) {
            try {
                final CtMethod targetMethod = targetClass.getMethod(target.methodName, target.descriptor);
                if (isAbstract(targetMethod.getModifiers())) {
                    call.setAbstract(true);
                }
            } catch (NotFoundException ex) {
                LOGGER.fine(() -> String.format("Target method not found in class: %s%n -> call at %s", target.toString(), call));
                mCounters.missingMethods++;
            }
        }
    }

    /**
     * Analyze control flow in the specified method for unambiguous allocations before and after calls.
     *
     * @param method The method to analyze.
     */
    private void analyzeControlFlow(CtBehavior method) {
        if (method.getMethodInfo().getCodeAttribute() == null) {
            return;
        }

        final MethodName name = MethodName.create(method, cache);
        try {
            final ConstPool cp = method.getMethodInfo().getCodeAttribute().getConstPool();
            final CodeIterator code = method.getMethodInfo().getCodeAttribute().iterator();
            final NavigableMap<Integer, CallSite> calls = mIt.streamTargets(name).collect(TreeMap::new, (map, cs) -> map.put(cs.getCallIndex(), cs), TreeMap::putAll);
            if (calls.isEmpty()) {
                return;
            }

            final ControlFlow cfa = ControlFlow.of(method);
            for (Block b : cfa.getBlocks()) {
                code.move(b.getPosition());
                for (int end = b.getEnd(), bci; code.hasNext() && (bci = code.next()) < end; ) {
                    final int op = code.byteAt(bci);
                    final String className;
                    switch (op) {
                        case Opcode.NEW:
                        case Opcode.MULTIANEWARRAY:
                            className = cp.getClassInfoByDescriptor(code.u16bitAt(bci + 1));
                            break;
                        case Opcode.ANEWARRAY:
                            className = "[" + cp.getClassInfoByDescriptor(code.u16bitAt(bci + 1));
                            break;
                        case Opcode.NEWARRAY:
                            final int atype = code.byteAt(bci + 1);
                            if (atype >= ATYPE_MIN && atype <= ATYPE_MAX) {
                                className = "[" + ATYPE_DESCRIPTORS[atype - ATYPE_MIN];
                            } else {
                                LOGGER.warning(String.format("Unknown atype of %d in newarray instruction at %s:%d", atype, name, bci));
                                continue;
                            }
                            break;

                        default:
                            continue;
                    }
                    for (CallSite cs : getCallsBefore(calls, b, bci)) {
                        cs.addAllocationAfter(cache.get(className));
                    }
                    for (CallSite cs : getCallsAfter(calls, b, bci)) {
                        if (op == Opcode.MULTIANEWARRAY) {
                            // Can't say anything about this statically:
                            // It's possible for any dimension to be 0, so arrays of all the following dimensions will not be allocated.
                            // Also, we wouldn't know how many arrays would be allocated anyway.
                            cs.clearAllocationsBefore();
                        } else {
                            cs.addAllocationBefore(cache.get(className));
                        }
                    }
                }
            } // for(Block : cfa)
        } catch (BadBytecode ex) {
            throw new Error("This can not happen (a method already parsed suddenly contains invalid bytecode).", ex);
        }
    }

    /**
     * Analyze data flow in the specified method to determine the most specific types for variables and return values.
     *
     * @param method The method to analyze.
     */
    private void analyzeDataFlow(CtBehavior method) {
        final MethodName name = MethodName.create(method, cache);
        final Map<CallSite, MethodName> newTargets = new HashMap<>();
        try {
            final Frame[] frames = (new Analyzer()).analyze(method.getDeclaringClass(), method.getMethodInfo());
            if (frames == null) {
                return;
            }
            for (CodeIterator code = method.getMethodInfo().getCodeAttribute().iterator(); code.hasNext(); ) {
                final int bci = code.next();
                final int op = code.byteAt(bci);
                switch (op) {
                    case Opcode.ARETURN:
                        // TODO specialize return types
                        break;

                    case Opcode.INVOKEINTERFACE:
                    case Opcode.INVOKEVIRTUAL:
                        final CallSite call = mIt.findCall(name, bci);
                        assert call != null;
                        if (call.getTarget().isConstructor()) {
                            // I don't know if valid bytecode should do that, but it happens
                            continue;
                        }

                        final Frame frame = frames[bci];
                        if (frame == null) {
                            LOGGER.warning(String.format("Missing stack frame for call at %s, skipping type analysis.", call));
                            // Data flow analysis is wrong or the class definition changed, in either case we can't trust anything
                            return;
                        }
                        // Signature-polymorphic methods can be handled as any other method.
                        // Besides, we couldn't tell if a method is signature-polymorphic anyway.
                        final MethodName target = call.getTarget();
                        final int targetSlot = frame.getTopIndex() - target.getCallStackSize();
                        if (targetSlot < 0) {
                            final String msg = "Required call stack (%d) larger than current stack (%d) for call to %s (at %s).";
                            LOGGER.warning(String.format(msg, target.getCallStackSize(), frame.getTopIndex(), target, call));
                            // I don't think it's safe to continue in this case
                            return;
                        }
                        final CtClass targetClass = frame.getStack(targetSlot).getCtClass();
                        if (targetClass != null && !sameClass(target, targetClass)) {
                            final String clazz = (targetClass.isArray() ? toJavaName(Descriptor.of(targetClass)) : targetClass.getName());
                            newTargets.put(call, new MethodName(clazz, target.methodName, target.descriptor, cache));
                        }
                        break;

                    default:
                        continue;
                }
            }
        } catch (NullPointerException ex) {
            // MW 2018/11/14: Occurs at
            // final Frame[] frames = (new Analyzer()).analyze(method.getDeclaringClass(), method.getMethodInfo());
            // for easyTravel trace...
            ex.printStackTrace();
            return;
        } catch (RuntimeException ex) {
            if (ex instanceof IllegalStateException) {
                // Some class referenced in the exception table was not loaded and is therefore not in the class pool
                return;
            }
            if (ex.getClass() == RuntimeException.class && ex.getCause() instanceof NotFoundException) {
                // Some class referenced in the method body was not loaded and is therefore not in the class pool
                return;
            }
            throw ex;
        } catch (BadBytecode ex) {
            // Executor throws BadBytecode when it cannot resolve a class, we don't care
            return;
        } catch (StackOverflowError ex) {
            // FIXME Caused by javassist.bytecode.analysis.Analyzer::analyze, needs to be fixed there
            // Cause is scalariform.formatter.preferences.IntegerPreference.parseValue(java.lang.String)
            LOGGER.warning("Stack overflow in Javassist Analyzer for method " + method.getLongName());
            // We haven't changed the analyzer state at this point, so it should be safe to just ignore the stack overflow
            return;
        }

        for (Map.Entry<CallSite, MethodName> entry : newTargets.entrySet()) {
            final CallSite call = entry.getKey();
            final MethodName target = entry.getValue();

            LOGGER.finest(() -> {
                final String msg = "Target class specialized from %s to %s at %s:%d.";
                return String.format(msg, call.getTarget().className, target.className, call.getCaller(), call.getCallIndex());
            });
            mCounters.specializedCallTargets++;

            mIt.setTarget(call, target);
        }
    }

    /**
     * Get all superclasses and implemented interfaces for the specified class.
     *
     * @param clazz The class.
     * @return A set of all superclasses and interfaces implemented by {@code clazz}.
     * @throws NotFoundException If {@link CtClass#getSuperclass()} or {@link CtClass#getInterfaces()} throw this exception.
     */
    private Set<CtClass> getAllSupers(CtClass clazz) throws NotFoundException {
        final Set<CtClass> supers = mSuperclasses.get(clazz);
        if (supers.isEmpty()) {
            final Deque<CtClass> queue = new ArrayDeque<>();
            queue.addLast(clazz);
            while (!queue.isEmpty()) {
                final CtClass next = queue.removeFirst();
                final CtClass superclass = next.getSuperclass();
                if (superclass != null && supers.add(superclass)) {
                    queue.addLast(superclass);
                }
                Arrays.stream(next.getInterfaces()).filter(supers::add).forEach(queue::addLast);
            }
        }
        return supers;
    }

    /**
     * Find all calls that come before the specified bytecode index.
     *
     * @param calls A map from BCI to {@linkplain CallSite call sites}.
     * @param start The current basic block to start from.
     * @param bci   The BCI of the allocation instruction.
     * @return An array of calls before the current allocation, may be empty.
     */
    private static CallSite[] getCallsBefore(NavigableMap<Integer, CallSite> calls, Block start, int bci) {
        final Set<CallSite> result = new HashSet<>();
        final Set<Block> visited = new HashSet<>();
        result.addAll(calls.subMap(start.getPosition(), true, bci, false).values());
        visited.add(start);

        Block b = start;
        while (b.enters() == 1) {
            b = b.enter(0);
            if (!visited.add(b)) {
                if (b.getPosition() != 0) {
                    LOGGER.info(() -> String.format("There seems to be an infinite loop somewhere around %s:%d", calls.firstEntry().getValue().getCallerName(), bci));
                }
                break;
            }
            result.addAll(calls.subMap(b.getPosition(), true, b.getEnd(), false).values());
        }
        return result.toArray(new CallSite[result.size()]);
    }

    /**
     * Find all calls that come after the specified bytecode index.
     *
     * @param calls A map from BCI to {@linkplain CallSite call sites}.
     * @param start The current basic block to start from.
     * @param bci   The BCI of the allocation instruction.
     * @return An array of calls after the current allocation, may be empty.
     */
    private static CallSite[] getCallsAfter(NavigableMap<Integer, CallSite> calls, Block start, int bci) {
        final Set<CallSite> result = new HashSet<>();
        final Set<Block> visited = new HashSet<>();
        result.addAll(calls.subMap(bci, false, start.getEnd(), false).values());
        visited.add(start);

        Block b = start;
        while (b.exits() == 1) {
            b = b.exit(0);
            if (!visited.add(b)) {
                if (b.getPosition() != 0) {
                    LOGGER.info(() -> String.format("There seems to be an infinite loop somewhere around %s:%d", calls.firstEntry().getValue().getCallerName(), bci));
                }
                break;
            }
            result.addAll(calls.subMap(b.getPosition(), true, b.getEnd(), false).values());
        }
        return result.toArray(new CallSite[result.size()]);
    }

    /**
     * Create a {@link ClassFile} object from the specified class definition.
     *
     * @param data The contents of a class file as a byte array.
     * @return The {@link ClassFile} object.
     * @throws BadClassDefinitionException in case the class file could not be parsed.
     */
    private static ClassFile makeClassFile(byte[] data) {
        try (final DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            return new ClassFile(in);
        } catch (IOException ex) {
            throw new at.jku.anttracks.callcontext.BadClassDefinitionException(data, ex);
        }
    }

    /**
     * Create a fully qualified method name for the specified method.
     *
     * @param clazz  The class the method belongs to.
     * @param method The method to create a name for.
     * @return The fully qualified name of the method.
     */
    private static MethodName makeMethodName(ClassFile clazz, MethodInfo method, Cache<String> cache) {
        return new MethodName(clazz.getName(), method.getName(), method.getDescriptor(), cache);
    }

    /**
     * Test whether the specified value is in the specified range.
     *
     * @param value The value to test.
     * @param lower The lower limit of the range, inclusive.
     * @param upper The upper limit of the range, inclusive.
     * @return {@code true} iff {@code lower} &leq; {@code value} &leq; {@code upper}.
     */
    private static boolean inRange(int value, int lower, int upper) {
        return lower <= value && value <= upper;
    }

    /**
     * Compare the class name from the specified method name and the specified class for equality.
     *
     * @param name  The method name to compare the class name from.
     * @param clazz The class to compare {@code name}'s class name to.
     * @return {@code true} if the class name of {@code method} refers to {@code clazz}.
     */
    private static boolean sameClass(MethodName name, CtClass clazz) {
        if ((name.className.charAt(0) == '[') != clazz.isArray()) {
            // One is an array, the other is not
            return false;
        }
        return clazz.getName().equals(clazz.isArray() ? Descriptor.toClassName(name.className) : name.className);
    }
}
