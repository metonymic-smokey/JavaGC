

package at.jku.anttracks.callcontext.cfg;

import at.jku.anttracks.callcontext.util.ImmutableList;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.bytecode.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static javassist.bytecode.analysis.Util.getJumpTarget;

public class ControlFlow {

    private static final Logger LOGGER = Logger.getLogger(ControlFlow.class.getSimpleName());

    private final CtClass mClass;
    private final MethodInfo mMethod;
    private final ImmutableList<Block> mBlocks;
    private final ImmutableList<Block> mCatchBlocks;

    private ControlFlow(CtClass clazz, MethodInfo method, Collection<Block> blocks, Collection<Block> catchers) {
        mClass = clazz;
        mMethod = method;
        mBlocks = ImmutableList.of(blocks);
        mCatchBlocks = ImmutableList.of(catchers);
    }

    // Public Members =====================================================================================================================

    public CtClass getCtClass() {
        return mClass;
    }

    public MethodInfo getMethod() {
        return mMethod;
    }

    /**
     * Get all blocks.
     *
     * @return An immutable list of all blocks, sorted by bytecode index.
     */
    public List<Block> getBlocks() {
        return mBlocks;
    }

    /**
     * Get the number of blocks. This is a convenience method for {@code getBlocks().size()}.
     *
     * @return The number of blocks available.
     */
    public int blocks() {
        return mBlocks.size();
    }

    /**
     * Get the block at the specified index. This is a convenience method for {@code getBlocks().get(index)}.
     *
     * @param index The index.
     * @return The block at the specified index.
     * @throws IndexOutOfBoundsException If {@code index} is less than 0 or not less than {@linkplain #blocks() the number of blocks}.
     */
    public Block block(int index) {
        return mBlocks.get(index);
    }

    /**
     * Get the entry blocks for all exception handlers.
     *
     * @return An immutable list of entry blocks of exception handlers, sorted by bytecode index.
     */
    public List<Block> getCatchBlocks() {
        return mCatchBlocks;
    }

    // Creation ===========================================================================================================================

    /**
     * Analyze the control flow of the specified method.
     *
     * @param method The method to analyze.
     * @return {@link ControlFlow} for {@code method}.
     * @throws BadBytecode              If the method contains invalid bytecode.
     * @throws IllegalArgumentException If the method does not contain code.
     */
    public static ControlFlow of(CtBehavior method) throws BadBytecode {
        return of(method.getDeclaringClass(), method.getMethodInfo2());
    }

    /**
     * Analyze the control flow of the specified method.
     *
     * @param clazz  The class containing the method.
     * @param method The method to analyze.
     * @return {@link ControlFlow} for {@code method}.
     * @throws BadBytecode              If the method contains invalid bytecode.
     * @throws IllegalArgumentException If the method does not contain code.
     */
    public static ControlFlow of(CtClass clazz, MethodInfo method) throws BadBytecode {
        if (method.getCodeAttribute() == null) {
            throw new IllegalArgumentException("Method does not have code.");
        }

        final CodeIterator code = method.getCodeAttribute().iterator();
        final ExceptionTable exceptions = method.getCodeAttribute().getExceptionTable();

        final at.jku.anttracks.callcontext.cfg.Mark[] marks = makeMarks(code, exceptions);
        assert IntStream.range(0, exceptions.size()).map(exceptions::handlerPc).noneMatch(bci -> marks[bci] == null);

        final Collection<Block> blocks = makeBlocks(method, marks, code);
        assert !blocks.isEmpty();
        final Collection<Block> catchers = IntStream.range(0, exceptions.size())
                                                    .map(exceptions::handlerPc)
                                                    .mapToObj(bci -> blocks.stream().filter(b -> b.getPosition() == bci))
                                                    .flatMap(Function.identity())
                                                    .collect(ImmutableList.collector());

        final ControlFlow cfa = new ControlFlow(clazz, method, blocks, catchers);
        assert checkCfgAgainstJavassist(clazz, method, cfa);
        return cfa;
    }

    /**
     * Create marks for all branches.
     *
     * @param code       Code iterator for the method.
     * @param exceptions The exception table for the method (may be {@code null}).
     * @return Array of marks (has the same length as {@code code}).
     * @throws BadBytecode If the method contains invalid bytecode.
     */
    private static at.jku.anttracks.callcontext.cfg.Mark[] makeMarks(CodeIterator code, ExceptionTable exceptions) throws BadBytecode {
        final at.jku.anttracks.callcontext.cfg.Mark[] marks = new at.jku.anttracks.callcontext.cfg.Mark[code.getCodeLength()];
        assert marks.length > 0;

        // Collect all valid bytecode indices
        final List<Integer> instructions = new ArrayList<>();
        for (code.begin(); code.hasNext(); ) {
            instructions.add(code.next());
        }

        // Create marks for ordinary control flow (including JSR)
        at.jku.anttracks.callcontext.cfg.Mark.makeStart(marks, 0);
        for (int bci : instructions) {
            final int op = code.byteAt(bci);
            at.jku.anttracks.callcontext.cfg.Mark m;
            switch (op) {
                case Opcode.GOTO:
                case Opcode.JSR:
                    makeJumpMarks(marks, bci, getJumpTarget(bci, code), 3, true);
                    break;

                case Opcode.GOTO_W:
                case Opcode.JSR_W:
                    makeJumpMarks(marks, bci, getJumpTarget(bci, code), 5, true);
                    break;

                case Opcode.RET:
                    m = at.jku.anttracks.callcontext.cfg.Mark.makeEnd(marks, bci, true);
                    m.makeSuccessor(2);
                    break;

                case Opcode.WIDE:
                    if (code.byteAt(bci + 1) == Opcode.RET) {
                        m = at.jku.anttracks.callcontext.cfg.Mark.makeEnd(marks, bci, true);
                        m.makeSuccessor(4);
                    }
                    break;

                case Opcode.TABLESWITCH:
                    markTableswitch(marks, code, bci);
                    break;

                case Opcode.LOOKUPSWITCH:
                    markLookupswitch(marks, code, bci);
                    break;

                default:
                    if (inRange(op, Opcode.IFEQ, Opcode.IF_ACMPNE) || op == Opcode.IFNULL || op == Opcode.IFNONNULL) {
                        makeJumpMarks(marks, bci, getJumpTarget(bci, code), 3, false);

                    } else if (inRange(op, Opcode.IRETURN, Opcode.RETURN) || op == Opcode.ATHROW) {
                        m = at.jku.anttracks.callcontext.cfg.Mark.makeEnd(marks, bci, true);
                        m.makeSuccessor(1);
                    }
                    break;
            }
        } // for(bci : instructions)

        // Link fallthrough
        for (int j = /* skip entry mark */ 1; j < instructions.size(); j++) {
            final at.jku.anttracks.callcontext.cfg.Mark m = marks[instructions.get(j)];
            if (m != null && m.isBegin()) {
                final at.jku.anttracks.callcontext.cfg.Mark end = at.jku.anttracks.callcontext.cfg.Mark.makeEnd(marks, instructions.get(j - 1), false);
                if (!end.isUnconditional()) {
                    end.link(m);
                }
            }
        } // for(j = 0..instructions.size())

        // Create marks for catch blocks
        IntStream.range(0, exceptions.size()).map(exceptions::handlerPc).forEach(bci -> at.jku.anttracks.callcontext.cfg.Mark.makeStart(marks, bci));

        return marks;
    }

    /**
     * Create three marks: end mark at the jump instruction, start mark at the next instruction, and start mark at the jump target.
     *
     * @param marks           The array of marks indexed by bytecode index.
     * @param bci             The bytecode index of the jump instruction.
     * @param targetBci       The bytecode index of the jump target.
     * @param size            The size of the jump instruction.
     * @param isUnconditional Whether the jump is unconditional.
     * @return The end mark at the jump instruction.
     */
    private static at.jku.anttracks.callcontext.cfg.Mark makeJumpMarks(at.jku.anttracks.callcontext.cfg.Mark[] marks, int bci, int targetBci, int size, boolean isUnconditional) {
        final at.jku.anttracks.callcontext.cfg.Mark end = at.jku.anttracks.callcontext.cfg.Mark.makeEnd(marks, bci, isUnconditional);
        end.makeJump(targetBci);
        end.makeSuccessor(size);
        return end;
    }

    /**
     * Generate marks for a {@code tableswitch} instruction.
     *
     * @param marks The array of marks indexed by bytecode index.
     * @param code  The {@link CodeIterator}.
     * @param bci   The bytecode index of the {@code tableswitch} instruction.
     */
    private static void markTableswitch(at.jku.anttracks.callcontext.cfg.Mark[] marks, CodeIterator code, int bci) {
        final int defaultBci = (bci & ~3) + 4;
        final int low = code.s32bitAt(defaultBci + 4);
        final int high = code.s32bitAt(defaultBci + 8);
        final int cases = high - low + 1;
        final int instructionSize = (defaultBci - bci) + 12 + 4 * cases;

        final at.jku.anttracks.callcontext.cfg.Mark m = at.jku.anttracks.callcontext.cfg.Mark.makeEnd(marks, bci, false);
        m.makeJump(bci + code.s32bitAt(defaultBci));
        for (int pos = defaultBci + 12; pos < bci + instructionSize; pos += 4) {
            m.makeJump(bci + code.s32bitAt(pos));
        }
    }

    /**
     * Generate marks for a {@code lookupswitch} instruction.
     *
     * @param marks The array of marks indexed by bytecode index.
     * @param code  The {@link CodeIterator}.
     * @param bci   The bytecode index of the {@code lookupswitch} instruction.
     */
    private static void markLookupswitch(at.jku.anttracks.callcontext.cfg.Mark[] marks, CodeIterator code, int bci) {
        final int defaultBci = (bci & ~3) + 4;
        final int cases = code.s32bitAt(defaultBci + 4);
        final int instructionSize = (defaultBci - bci) + 8 + 8 * cases;

        final at.jku.anttracks.callcontext.cfg.Mark m = at.jku.anttracks.callcontext.cfg.Mark.makeEnd(marks, bci, false);
        m.makeJump(bci + code.s32bitAt(defaultBci));
        for (int pos = defaultBci + 8; pos < bci + instructionSize; pos += 8) {
            m.makeJump(bci + code.s32bitAt(pos + 4));
        }
    }

    /**
     * Create basic blocks from the specified marks.
     *
     * @param method The method being analyzed.
     * @param marks  The array of marks.
     * @param code   The method code.
     * @return A list of basic blocks (with the control flow of subroutines duplicated for every call site), sorted by bytecode index.
     * @throws BadBytecode If the method contains invalid bytecode.
     */
    private static Collection<Block> makeBlocks(MethodInfo method, at.jku.anttracks.callcontext.cfg.Mark[] marks, CodeIterator code) throws BadBytecode {
        final NavigableMap<Integer, Block> blocks = new TreeMap<>();

        final int[] starts = IntStream.range(0, marks.length).filter(idx -> marks[idx] != null && marks[idx].isBegin()).toArray();
        for (int startIdx : starts) {
            code.move(startIdx);
            code.next();
            int endIdx;
            while ((endIdx = code.lookAhead()) < marks.length) {
                assert code.hasNext();
                code.next();
                if (marks[endIdx] != null) {
                    if (!marks[endIdx].isBegin()) {
                        endIdx = code.lookAhead();
                    }
                    break;
                }
            }
            final Block b = new Block(method, startIdx, endIdx - startIdx);
            blocks.put(startIdx, b);
        }

        linkBlocks(marks, blocks);
        final List<Block> result = cloneSubroutines(blocks, code);
        result.sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
        return result;
    }

    /**
     * Link the specified basic blocks together.
     *
     * @param marks  The array of marks.
     * @param blocks The basic blocks to link (map from start bytecode index to basic blocks at that index).
     */
    private static void linkBlocks(at.jku.anttracks.callcontext.cfg.Mark[] marks, NavigableMap<Integer, Block> blocks) {
        final Map<Block, List<Block>> successors = new HashMap<>();
        for (Entry<Integer, Block> entry : blocks.entrySet()) {
            final Block block = entry.getValue();
            final at.jku.anttracks.callcontext.cfg.Mark mark = marks[entry.getKey()];
            assert mark != null;
            final List<Block> predecessors = mark.getIncoming()
                                                 .stream()
                                                 .map(m -> m.isBegin() ? blocks.get(m.getPosition()) : blocks.lowerEntry(m.getPosition()).getValue())
                                                 .collect(Collectors.toList());
            assert predecessors.stream().noneMatch(Objects::isNull);
            block.setIncomings(predecessors);
            for (Block predecessor : predecessors) {
                successors.computeIfAbsent(predecessor, key -> new ArrayList<>()).add(block);
            }
        }
        successors.entrySet().stream().forEach(entry -> entry.getKey().setOutgoings(entry.getValue()));
    }

    /**
     * Duplicate the control flow for subroutines.
     *
     * @param blocks The basic blocks without duplicates (map from start bytecode index to basic blocks at that index).
     * @param code   The method code.
     * @return A list of basic blocks in no particular order, with the control flow of subroutines duplicated for every call site.
     * @throws BadBytecode If the method contains invalid bytecode.
     */
    private static List<Block> cloneSubroutines(NavigableMap<Integer, Block> blocks, CodeIterator code) throws BadBytecode {
        final List<Block> result = new ArrayList<>(blocks.size());
        final Map<Block, List<Block>> subroutines = collectSubroutines(blocks, code);
        // Copy all blocks which are not part of a subroutine
        {
            final Set<Block> subroutineBlocks = subroutines.values().stream().flatMap(List::stream).collect(Collectors.toSet());
            blocks.values().stream().filter(b -> !subroutineBlocks.contains(b)).forEach(result::add);
        }
        // Clone and link subroutine blocks for each call site
        for (code.begin(); code.hasNext(); ) {
            final int bci = code.next();
            final int op = code.byteAt(bci);
            if (isJsr(op)) {
                final Block caller = blocks.floorEntry(bci).getValue();
                assert caller.exits() == 1;
                Block target = blocks.get(getJumpTarget(bci, code));
                assert target != null && subroutines.containsKey(target);
                final List<Block> clones = cloneSubroutine(subroutines.get(target));

                target = clones.get(0);
                caller.setOutgoing(target);
                target.setIncomings(Stream.concat(Stream.of(caller), target.getPredecessors().stream()).collect(ImmutableList.collector()));

                final Block successor = blocks.higherEntry(bci).getValue();
                final List<Block> returns = new ArrayList<>();
                for (Block clone : clones) {
                    if (clone.exits() == 0 && getLastInstruction(clone, code) == Opcode.RET) {
                        clone.setOutgoing(successor);
                        returns.add(clone);
                    }
                }
                successor.setIncomings(Stream.concat(successor.getPredecessors().stream(), returns.stream()).collect(ImmutableList.collector()));
            }
        } // for(bci : code)
        return result;
    }

    /**
     * Get a map of all subroutines.
     *
     * @param blocks The basic blocks without duplicates (map from start bytecode index to basic blocks at that index).
     * @param code   The method code.
     * @return A map from start blocks of subroutines to all basic blocks within that subroutine (including the start block itself, which is
     * the first entry in the list).
     * @throws BadBytecode If the method contains invalid bytecode.
     */
    private static Map<Block, List<Block>> collectSubroutines(Map<Integer, Block> blocks, CodeIterator code) throws BadBytecode {
        final Map<Block, List<Block>> result = new HashMap<>();
        for (Block b : blocks.values()) {
            if (isJsr(getLastInstruction(b, code))) {
                assert b.exits() == 1;
                result.computeIfAbsent(b.exit(0), ControlFlow::collectSubroutine);
            }
        }
        return result;
    }

    private static List<Block> collectSubroutine(Block start) {
        final Set<Block> blocks = new HashSet<>();
        final Deque<Block> queue = new ArrayDeque<>();
        // For the start block add only successors (we don't want to collect callers)
        blocks.add(start);
        queue.addAll(start.getSuccessors());
        while (!queue.isEmpty()) {
            final Block next = queue.removeFirst();
            if (blocks.add(next)) {
                // For all other blocks in the subroutine, add both successors and predecessors (to collect potentially dead blocks)
                queue.addAll(next.getSuccessors());
                queue.addAll(next.getPredecessors());
            }
        }
        // Make sure start block stays first in the list
        final List<Block> result = new ArrayList<>(blocks.size());
        blocks.remove(start);
        result.add(start);
        result.addAll(blocks);
        result.subList(1, result.size()).sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
        return result;
    }

    /**
     * Create clones of all blocks in the specified list, preserving the control flow between them.
     *
     * @param subroutine A list of all blocks in a single subroutine.
     * @return A list of cloned blocks, in the same order as the prototypes (which means the start block is the first entry in the list, if
     * {@code subroutine} has been obtained from a call to {@link #collectSubroutines(Map, CodeIterator)}).
     */
    private static List<Block> cloneSubroutine(List<Block> subroutine) {
        final List<Block> result = subroutine.stream().map(Block::new).collect(Collectors.toCollection(ArrayList::new));

        final Map<Block, Block> clones = new IdentityHashMap<>();
        for (Iterator<Block> in = subroutine.iterator(), out = result.iterator(); in.hasNext(); ) {
            clones.put(in.next(), out.next());
        }

        final ArrayList<Block> tmp = new ArrayList<>();
        // Link successors
        for (Block b : subroutine) {
            assert clones.keySet().containsAll(b.getSuccessors()) : "List of subroutine blocks is not closed (successors).";
            b.getSuccessors().stream().map(clones::get).forEach(tmp::add);
            clones.get(b).setOutgoings(tmp);
            tmp.clear();
        }
        // Start block of subroutine will have predecessors outside the subroutine (i.e. the subroutine's callers), don't add them
        {
            final Block start = subroutine.get(0);
            start.getPredecessors().stream().map(clones::get).filter(Objects::nonNull).forEach(tmp::add);
            clones.get(subroutine.get(0)).setIncomings(tmp);
        }
        // Link predecessors
        for (Block b : subroutine.subList(1, subroutine.size())) {
            assert clones.keySet().containsAll(b.getPredecessors()) : "List of subroutine blocks is not closed (predecessors).";
            tmp.clear();
            b.getPredecessors().stream().map(clones::get).forEach(tmp::add);
            clones.get(b).setIncomings(tmp);
        }
        return result;
    }

    // Utility Methods ====================================================================================================================

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
     * Get the last instruction in the specified basic block.
     *
     * @param b    The basic block.
     * @param code The method code.
     * @return The opcode of the last instruction in {@code b}.
     * @throws BadBytecode If the method contains invalid bytecode.
     */
    private static int getLastInstruction(Block b, CodeIterator code) throws BadBytecode {
        code.move(b.getPosition());
        int lastBci = code.next();
        while (code.lookAhead() < b.getEnd()) {
            assert code.hasNext();
            lastBci = code.next();
        }
        return code.byteAt(lastBci);
    }

    /**
     * Determine whether the specified opcode is a JSR instruction.
     *
     * @param op The opcode to test.
     * @return {@code true} if {@code op} is a {@link Opcode#JSR JSR} or {@link Opcode#JSR_W JSR_W} instruction.
     */
    private static boolean isJsr(int op) {
        return op == Opcode.JSR || op == Opcode.JSR_W;
    }

    /**
     * Check the control flow graph against the Javassist implementation for supported methods.
     *
     * @param clazz  The class containing the method.
     * @param method The method being analyzed.
     * @param cfa    The {@link ControlFlow} to check.
     * @return {@code true} (the necessary asserts are inside this method).
     */
    private static boolean checkCfgAgainstJavassist(CtClass clazz, MethodInfo method, ControlFlow cfa) {
        final javassist.bytecode.analysis.ControlFlow cfa2;
        try {
            cfa2 = new javassist.bytecode.analysis.ControlFlow(clazz, method);
        } catch (BadBytecode ex) {
            // Method contains JSR, which Javassist doesn't handle
            LOGGER.finest(() -> {
                final String msg = "JSR found, cannot compare control flows for method %s.%s%s";
                return String.format(msg, clazz.getName(), method.getName(), method.getDescriptor());
            });
            return true;
        } catch (ArrayIndexOutOfBoundsException ex) {
            // Javassist ControlFlow crashes for some methods, I don't know why yet
            LOGGER.info(() -> {
                final String msg = "Javassist ControlFlow crashed for method %s.%s%s";
                return String.format(msg, clazz.getName(), method.getName(), method.getDescriptor());
            });
            return true;
        }

        assert cfa2.basicBlocks() != null && cfa2.basicBlocks().length > 0;
        if (method.getCodeAttribute().getExceptionTable().size() != 0) {
            // Javassist ControlFlow creates basic blocks for try blocks, our ControlFlow does not
            return true;
        }
        assert cfa2.basicBlocks().length == cfa.getBlocks().size();

        final javassist.bytecode.analysis.ControlFlow.Block[] blocks2 = cfa2.basicBlocks().clone();
        Arrays.sort(blocks2, (a, b) -> Integer.compare(a.position(), b.position()));
        int j = 0;
        for (Block b : cfa.getBlocks()) {
            final javassist.bytecode.analysis.ControlFlow.Block b2 = blocks2[j++];
            assert b.getPosition() == b2.position();
            assert b.getLength() == b2.length();
        }
        j = 0;
        for (Block b : cfa.getBlocks()) {
            final javassist.bytecode.analysis.ControlFlow.Block b2 = blocks2[j++];
            assert b.getSuccessors()
                    .stream()
                    .map(Block::getPosition)
                    .collect(Collectors.toSet())
                    .equals(IntStream.range(0, b2.exits()).map(idx -> b2.exit(idx).position()).boxed().collect(Collectors.toSet()));
            assert b.getPredecessors()
                    .stream()
                    .map(Block::getPosition)
                    .collect(Collectors.toSet())
                    .equals(IntStream.range(0, b2.incomings()).map(idx -> b2.incoming(idx).position()).boxed().collect(Collectors.toSet()));
        }
        return true;
    }
}
