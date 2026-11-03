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

Local passes repeat until the CFG and values stabilize, including after an inline
trial. Scalar runtime dependencies are recalculated on the optimized IR, so
inlined parameters and compiler-owned return slots can become constants. Explicit
`external` declarations, captured addresses and unknown effects retain their
runtime constraints. The original semantic IR and its facts are preserved.

`call-save-elimination` and `stack-frame-planning` control storage lowering.
`minecraft-command-cleanup` runs on the resulting Minecraft IR. The pass manager
invalidates cached analyses after a changed CFG. Analysis objects belong to the
current module, rather than a process-wide cache.

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
decisions, calling conventions, opaque regions and final commands. Tests compare
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
