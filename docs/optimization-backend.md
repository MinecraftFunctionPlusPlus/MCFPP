# Optimization backend

MCFPP uses optimization level 2 by default. `mcfpp build -O0`, `-O1`, `-O2`,
or `--optimization-level=1` override the project setting. The project may set:

```toml
[optimization]
level = 2
disabledPasses = []

[optimization.costs]
command = 1
storage = 10
entityNbt = 60
playerNbt = 150
macroFunctionCommand = 1
macroBase = 1
macroCharacter = 0.01
macroArgument = 1
```

Weights are finite, nonnegative heuristic units, not measured time. A macro adds
base, character and substitution costs. Every ordinary command in a function
containing a macro also pays `macroFunctionCommand`. The estimator distinguishes
storage and entity NBT; explicit player selectors and named players use the
player weight. An ambiguous entity selector uses the entity estimate.

Level 0 retains language checking, type inference, compile-time binding and
required lowering. Level 1 enables local optimization and call/storage planning.
Level 2 additionally tries nonrecursive inlining. There is no function-size or
datapack-size threshold. Inline candidates must have structured control flow;
opaque commands prevent automatic inlining until their control behavior is known.

For diagnosis, `--disable-pass=automatic-inlining,common-subexpression-elimination`
disables optional transformations. Mandatory lowering and verification cannot be
disabled. CLI settings override file arguments, which override project settings.
The optimization configuration participates in generation cache identity.

## Representation and phases

The front end owns `TypedIR` and semantic facts. Optimization produces a separate
CFG; it never reruns a source macro or native host method. Each function keeps its
original semantic IR for language consumers. Backend generation is deferred until
project optimization, after semantic checks have published their results.

The function pass sequence is:

1. `constant-propagation`
2. `algebraic-simplification`
3. `copy-propagation`
4. `common-subexpression-elimination`
5. `control-flow-simplification`
6. `dead-value-elimination`
7. `automatic-inlining` (level 2), followed by local cleanup
8. `number-provider-fusion` (level 1 and 2, when the target supports native float providers)

Local passes repeat until the CFG and values stabilize, including after an inline
trial. Scalar runtime dependencies are recalculated on the optimized IR, so
inlined parameters and compiler-owned return slots can become constants. Explicit
`external` declarations, captured addresses and unknown effects retain their
runtime constraints. The original semantic IR and its facts are preserved.

`call-save-elimination` and `stack-frame-planning` control storage lowering.
`minecraft-command-cleanup` runs on the resulting Minecraft IR. The pass manager
invalidates cached analyses after a changed CFG. Analysis objects belong to the
current module, rather than a process-wide cache.

After backend generation, `context-region-fusion` lifts controlled context calls
into context regions with immutable component provenance. At levels 1 and 2 it
plans context and language frames independently, follows calls and macros for
saved-slot observations, and fuses ordered execute clauses. This runs before
library resource publication and final Minecraft command cleanup. A single
command callback can become `execute at @s on passengers run tp @s ~ ~ ~`;
unreferenced compiler context helpers are removed.

Generated macro bodies participate in the same region graph. Dynamic operands
remain prepared at their original call site, while the macro can contain one
fused execute chain. Ordinary callback text containing literal `$(...)` keeps a
function boundary so that it cannot capture the entry macro's arguments. Calls
through macros also retain callback ownership for entrypoint frame planning.

Multiple commands keep one terminal callback function, so selectors are evaluated
once and each branch completes its body in order. Local returns keep their
callback boundary. Ancestor snapshots, operand preparation, cleanup, captured
value environments, unknown calls and outer result stores constrain fusion.
Saved identities are still captured before the conflicting transition; frames
whose lexical depths are observed anywhere in the reachable call chain remain.
Debug command instrumentation retains the original boundaries. Disable this
pass with `--disable-pass=context-region-fusion`; `stack-frame-planning` also
controls region frame removal.

At the same boundary, `dead-value-elimination` removes unobserved native
`CommandResult` initialization, private temporary copies and result/success
stores. It retains the native operation and any result that is read, escapes to
another destination, or may be observed by a call. Known calls whose reachable
bodies cannot observe stack frames allow dead result removal; unresolved calls
and frame-reading calls retain the payloads. This transformation does not
discard argument effects or command side effects.

Provider fusion selects single-use float arithmetic results within a basic block
after local cleanup and inlining. Lowering embeds each selected definition into
its consumer's provider, including float comparisons, instead of writing and
reading an intermediate NBT float. For example, `(a+b)*(c-d)` uses one compute
command with nested `add` and `sub` inputs. Providers stay inline in commands;
this pass does not create registry resource files. Shared results stay materialized.
Reads retain their snapshots, and writes, calls and opaque commands bound fusion.
The IR dump marks selected definitions as `[fused provider input]`.

Fusion keeps the expression's binary tree and float rounding order. It follows
native nested-provider behavior for exceptional values: non-finite intermediate
results propagate through the provider tree until the enclosing evaluation.
Consequently, exceptional expressions can differ from O0, which converts each
materialized non-finite arithmetic result to positive zero. Use
`--disable-pass=number-provider-fusion` to keep those intermediate boundaries.

Calls use function-specific score registers for scalar parameters and returns
when the known call chain permits it. Nonrecursive, closed functions use private
static NBT storage for values requiring NBT. Recursive or unknown/reentrant paths
retain frames. Caller saves are limited to live values that the callee can
clobber. These are separate decisions from the front end's `Effect.Pure`, which
does not prove that a call is removable or deterministic.

Inlining renames declarations, result IDs and blocks, binds evaluated arguments,
joins return exits, and captures values that span the insertion. It excludes
recursive strongly connected components. The provisional typed cost provider
uses the Minecraft command cost classifier, includes reachable callees, sums
sequences and takes the maximum branch. `InlineCostEstimator` is the replacement
point for a more precise backend simulator.

Minecraft IR represents score operations, NBT locations, execute clauses, calls
and frame operations. Existing `Command` producers enter through the compatibility
lowering boundary. Unknown commands remain opaque and keep their order. This
allows incremental migration of native and legacy producers without guessing
their effects from arbitrary command text.

The compiler's stack depth, slot names and scratch scores are implementation
details. Direct dependencies on them have undefined behavior. Public function
IDs, declared exports, resource references and lifecycle tags remain entry roots.

## Inspection and verification

`--dump-ir` includes the semantic and optimized CFGs, pass changes, inline cost
decisions, calling conventions, opaque regions, final commands and macro bodies. Tests compare
observable results across all three levels. The command executor also records
executed commands and frame operations, so a smaller file alone does not establish
an optimization benefit.

Arithmetic transformations use the selected runtime representation's rules.
Floating-point reassociation and identities requiring stronger failure or numeric
proofs are not applied. Unknown effects, alias writes and externally observable
stores remain barriers to removal or reuse.

Integer simplification combines constant offsets and factors and removes neutral
or absorbing operations. Removing an expression result does not remove argument
effects. Copy propagation uses captured SSA values, so subsequent writes do not
change earlier snapshots. Scalar lowering can write a single-use result directly
to its destination and use `scoreboard players add/remove` for literal offsets;
it retains operand snapshots when the destination would overwrite an input.

For example, given `increment(value) { return value + 1; }` and
`wrapper(value) { return increment(value); }`, O2 publishes `wrapper(4)` as `5`
with one score assignment. Given a runtime `input`, inlining
`return ((input + 2) + 3) * 1 + 0` needs a copy to the result and one score addition,
without call or frame commands. Tests execute these outputs and compare their
values and effects with O0 and O1.
