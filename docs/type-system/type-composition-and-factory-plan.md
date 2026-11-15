# MCFPP Type Composition and Factory Construction --- Implementation Plan

> **Purpose:** Agent-executable implementation plan for the Literal
> Object Type, intersection/union type, structural subtyping,
> inheritance composition, and `new` factory redesign.
>
> **Repository:** `MinecraftFunctionPlusPlus/MCFPP`
>
> **Implementation rule:** Treat this document as the design contract.
> Before changing code, inspect the current branch and implementation
> because the plan may be applied to a newer local state. Preserve
> existing working behavior unless it conflicts with an explicit
> invariant below. Do not silently invent semantics for unresolved
> questions; record them and ask for a decision.

## 0. Agent operating instructions

1.  Inspect the current repository state, active branch, recent commits,
    and relevant type-system documentation before editing.
2.  Identify the current implementations of:
    -   type identity and normalization;
    -   subtype and assignability relations;
    -   `data` / object-template declarations and field metadata;
    -   inheritance expansion and validation;
    -   `as` reinterpretation;
    -   object literals and expression type inference;
    -   function declarations, receiver binding, overload resolution,
        and call lowering;
    -   field initializers and current constructor/init generation;
    -   storage layout, `Place`, field paths, and write invalidation.
3.  Produce a short implementation map with file paths and existing
    tests. Do not assume paths or APIs from an older checkout.
4.  Implement in dependency order. Keep each phase independently
    buildable and tested where practical.
5.  Add regression tests before or alongside behavior changes. Prefer
    focused tests over broad snapshots.
6.  Run the narrowest relevant test suite after each phase, then the
    full test suite and project validation required by the repository.
7.  Do not claim a test passed unless it was actually executed. Record
    unavailable toolchains or environmental blockers explicitly.
8.  Do not implement optional object spread until the core object
    literal and factory model is stable.
9.  Do not change user-facing syntax beyond the explicitly specified
    syntax without approval.
10. Update type-system documentation and migration notes as behavior
    becomes implemented.

------------------------------------------------------------------------

## 1. Design contract and invariants

### 1.1 Data and behavior are separate

-   A data template describes a data contract and the data layout
    derived from that contract.
-   Functions declared in a data-template body are semantically free
    functions with a receiver parameter bound by member-call syntax.
-   A subtype relationship does **not** copy, inherit, override, or
    automatically expose a parent template's functions.
-   Function lookup and receiver compatibility belong to the function
    system, not to data inheritance.
-   Do not introduce method-resolution rules, `super` dispatch,
    inherited method bodies, or diamond method resolution as part of
    this work.

### 1.2 Literal Object Types

-   A Literal Object Type describes a set of named fields and their
    field contracts, e.g. `{ x: int, y: string }`.
-   Object types are **open**: a value may have additional fields beyond
    those required by the target type.
-   A required field must be present.
-   An optional field may be absent; if present, its value must satisfy
    the field type.
-   Template field nullable syntax (e.g. `var y: int?`) is syntax sugar
    for an optional/may-be-absent field. It does **not** mean the
    field's value may be `null`.
-   Nullable/optional-field syntax is only available in template field
    declarations. Do not introduce it as a general type constructor or
    permit it in arbitrary local variables, parameters, or return types
    as part of this plan.
-   Field initializers are not part of type identity or structural
    compatibility.

### 1.3 Literal types and `const`

-   `const` denotes a compile-time class constant, not a mutable
    variable with a readonly flag.
-   A const must be initialized at declaration with a compile-time-known
    value (literal or compile-time evaluable expression).
-   The const's type is the corresponding literal type. Examples:
    `const n = 1` has type `1`; `const s = "hi"` has type `"hi"`.
-   Literal types are subtypes of their primitive parent types:
    `1 <: int`, `"hi" <: string`, `true <: bool`.
-   Const is not assignable after declaration. Existing rules preventing
    mutation of const members, including indirect mutation through
    methods, remain enforced.
-   Do not expose syntax such as `var n: 1` to users unless separately
    approved; it is an internal semantic model for now.

### 1.4 Intersection types

-   `A & B` means a value must satisfy both contracts.
-   Intersection is associative, commutative, and idempotent:
    -   `A & B = B & A`
    -   `(A & B) & C = A & (B & C)`
    -   `A & A = A`
-   Normalize nested intersections, remove duplicates, and produce
    deterministic canonical identities.
-   For same-name fields, intersect their field types.
-   Requiredness of a merged field is the logical OR of requiredness:
    -   required + optional =\> required;
    -   optional + optional =\> optional.
-   A conflicting required field makes the whole object intersection
    `never`.
-   A conflicting optional field may remain as an optional-never
    constraint (conceptually a field that may be absent but cannot be
    present). Do not collapse the whole object to `never` solely because
    an optional field's type intersection is `never`.
-   Example results:
    -   `{x:int} & {x:1}` =\> `{x:1}`
    -   `{x:int} & {x:bool}` =\> `never`
    -   `{x:int} & {x?:int}` =\> `{x:int}`
    -   `{x?:int} & {x?:bool}` =\> `{x?:never}`
-   Ordinary field and const field are distinct member kinds for
    **inheritance declaration validation**, but intersection itself
    computes type constraints. Example: `{x:int} & {x:1}` =\> `{x:1}`.

### 1.5 Union types

-   `A | B` means a value may satisfy either alternative.
-   Normalize nested unions, remove duplicates, eliminate alternatives
    subsumed by broader alternatives, and create deterministic canonical
    identities.
-   Member access on a union is allowed only for members guaranteed by
    every possible alternative.
-   The common member's read/write contract must be safe for every
    alternative. Do not infer write safety merely from a common read
    type.
-   Preserve existing candidate/exact/unknown actual-type analysis where
    applicable.

### 1.6 Structural subtyping

-   Structural compatibility is a first-class subtype/assignability
    relation, not merely a warning helper for `as`.
-   Open object subtyping is width-compatible: a source with extra
    fields may satisfy a target requiring fewer fields.
-   Every required target field must be present in the source.
-   Optional target fields may be absent from the source; if present,
    their values must satisfy the target field contract.
-   Field type compatibility follows the agreed intersection/subtype
    rules and must remain sound for shared writable storage.
-   Structural compatibility must be used consistently by assignment,
    initialization, function arguments, return values, overload
    resolution, and generic constraints where the language's existing
    rules call for subtype compatibility.

### 1.7 Inheritance is intersection plus extra declaration checks

A declaration:

``` mcfpp
data A : T1, T2 {
    var local: int
}
```

has the type-level meaning:

``` text
A = T1 & T2 & { local: int }
```

-   Inheritance does not copy functions.
-   Parent fields are flattened into one normalized data structure; no
    separate parent-field storage is created.
-   Same-name compatible ordinary fields merge into one logical
    field/storage slot.
-   Same-source fields reached through multiple inheritance paths
    naturally deduplicate through canonical field/type normalization.
-   Inheritance has stricter declaration validation than a plain
    intersection.
-   A same-name ordinary `var` field and `const` field across inherited
    parents is always an inheritance error, regardless of parent
    declaration order.
-   The same `var`/`const` combination in a plain intersection is
    handled by type intersection; e.g. `{x:int} & {x:1}` becomes
    `{x:1}`.
-   Preserve declaration provenance long enough to issue useful
    diagnostics before normalization erases source distinctions.
-   Do not use parent order to resolve conflicts or select a winning
    field.

### 1.8 `as` semantics

-   `as` is an explicit reinterpretation/view operation, not a checked
    structural conversion.
-   Do not enforce structural compatibility for `as`.
-   Nominal type relationships may be used to emit warnings in
    applicable cases.
-   Do not reject an `as` solely because the source and target object
    shapes are incompatible.
-   Preserve existing distinctions between a shared-place view and
    assignment/copy into an independent destination.
-   All writes must still obey the effective constraints of the actual
    shared location and const rules; permissive `as` must not become a
    write-safety bypass.

### 1.9 Functions and receiver binding

-   A function declared inside a data template is semantically a free
    function with an explicit receiver parameter of that template type.
-   Member syntax is a convenient way to bind the receiver.
-   Such functions are not inherited or copied when another template is
    a subtype.
-   A subtype value may call a function whose receiver type it
    satisfies, through ordinary receiver argument compatibility.
-   Function lookup, visibility, overload resolution, generic inference,
    and effects follow the ordinary function system.

### 1.10 Factory construction with `new`

-   Remove the special constructor/init model in favor of factory
    functions.
-   `new` is a dedicated declaration form; users do not write `func`, do
    not write a return type, and do not write an implicit `this`.
-   A `new` declaration is semantically a free function associated with
    its declaring template, returning that template's complete `Self`.
-   `new` participates in ordinary overload resolution.
-   `A(args...)` is construction-call syntax and resolves to the
    corresponding `A.new(args...)`.
-   A factory returns a complete value; the compiler does not create an
    uninitialized receiver and then require the factory to assign every
    field on it.
-   A `new` body must return a value whose type is a subtype of `Self`.
-   Parent `new` functions are never invoked automatically.
-   A child factory may call any parent or unrelated factory as an
    ordinary function if its arguments satisfy that function's
    receiver/parameter types; it must explicitly compose the returned
    data if desired.
-   A template's field initializers are used to synthesize an implicit
    `new` factory.
-   An explicit `new` with the same signature suppresses/replaces the
    corresponding implicit factory; other explicit overloads coexist
    with remaining implicit overloads.
-   If there is no valid implicit object value to construct, do not
    synthesize an invalid default factory.
-   Generic template parameters are available in the `new` declaration's
    enclosing template environment; `Self` refers to the fully
    instantiated template type.

### 1.11 Object literals

-   Object literals are expressions whose type is inferred from their
    fields and values.
-   Shorthand `{ i }` resolves `i` in lexical scope and is equivalent to
    `{ i: i }`.
-   Shorthand is not restricted to `new`; it is a general object-literal
    feature.
-   Object literal types are open and can contain extra fields.
-   A factory return is validated by ordinary structural subtype
    checking against `Self`.

### 1.12 Parent-qualified factory calls

Two forms have distinct rules:

1.  Receiver-bound form, actual syntax to be confirmed against the
    parser conventions, conceptually `this.Parent.new(args...)`:
    -   Only available where a `this` receiver exists: member functions
        and extension functions.
    -   `Parent` must be a nominal ancestor of the static receiver type;
        structural compatibility alone is insufficient.
    -   It binds the current `this` as the receiver argument.
    -   Parentheses in earlier explanatory notation such as
        `this.(Parent)` were meta-notation, not literal syntax.
2.  Explicit free-function form: `Parent.new(this, args...)`:
    -   Ordinary function call.
    -   Available anywhere ordinary function calls are available.
    -   No nominal ancestry requirement; ordinary receiver-argument type
        compatibility is sufficient.

Do not implement the receiver-bound syntax until the exact parser
spelling is confirmed from the current grammar/design. Preserve the
semantic distinction above.

------------------------------------------------------------------------

## 2. Phase 0 --- Repository reconnaissance and baseline

### Tasks

-   Inspect current branch, working tree, latest commits, and current
    type-system specification.
-   Locate all relevant implementation classes and tests.
-   Trace existing `data` declaration parsing and field metadata.
-   Trace `TypeId`, type equality/hash, type normalization, `isSubtype`,
    assignability, implicit conversions, and overload ranking.
-   Trace field layout construction and how inherited fields are
    represented.
-   Trace `as`, `TypedView`, `ValueRef`, `Place`, `Location`, and
    storage writes.
-   Trace object literal grammar/visitor/IR support, if any.
-   Trace current constructor/init generation and `A()` lowering.
-   Identify persistence/serialization formats for compiled templates
    and generic instances.
-   Record baseline test results.

### Deliverables

-   `docs/type-system/...` implementation map or equivalent issue/notes.
-   List of impacted files and test suites.
-   Baseline build/test status and toolchain version.

### Acceptance criteria

-   No code changes in this phase.
-   Every later implementation task has identified source and test
    locations.
-   Existing branch behavior is documented accurately.

------------------------------------------------------------------------

## 3. Phase 1 --- Core type representation: Literal Object Types and literal types

### Tasks

1.  Add or adapt an immutable object-type representation containing
    canonical field descriptors.
2.  Define field descriptor data:
    -   name;
    -   field type;
    -   required/optional presence;
    -   member kind needed for inheritance validation (ordinary field vs
        compile-time const);
    -   provenance/origin metadata for diagnostics, kept outside
        canonical semantic identity where possible.
3.  Ensure field initializers and function declarations do not
    participate in object type identity.
4.  Add literal types for compile-time-known primitive values.
5.  Define literal-to-primitive subtype relationships.
6.  Ensure const declaration captures a compile-time-known immutable
    value and exposes its literal type.
7.  Ensure const cannot be mutated directly or indirectly.
8.  Keep optional-field syntax restricted to template field
    declarations.

### Tests

-   Canonical identity for object types with same fields in different
    declaration order.
-   Open-object width subtype examples.
-   Required versus optional field identity.
-   Literal type identity and parent primitive subtype.
-   Const literal value inference.
-   Const declaration rejects non-compile-time-known values.
-   Const mutation through direct field access and method calls is
    rejected.
-   Field initializer changes do not change type identity.

### Acceptance criteria

-   Object and literal types have stable equality/hash/canonicalization.
-   No runtime storage representation is introduced solely for an
    intersection/object type.
-   Existing template types can expose their normalized object structure
    without losing nominal declaration identity.

------------------------------------------------------------------------

## 4. Phase 2 --- Intersection and union normalization

### Tasks

1.  Implement canonical intersection normalization:
    -   flatten nested intersections;
    -   remove duplicates;
    -   sort deterministically;
    -   simplify `T & never` to `never`;
    -   merge object fields.
2.  Implement same-name field merge:
    -   intersect field types;
    -   requiredness = logical OR;
    -   required + `never` =\> whole object `never`;
    -   optional + `never` =\> optional-never field.
3.  Implement canonical union normalization:
    -   flatten nested unions;
    -   remove duplicates;
    -   remove alternatives subsumed by another alternative;
    -   sort deterministically.
4.  Ensure normalization terminates for recursive/nested object
    structures; use cycle-safe memoization or an equivalent established
    strategy.
5.  Ensure generic type arguments are normalized consistently without
    accidentally changing nominal generic identity.
6.  Preserve `never` as bottom type and ensure existing top/any/object
    semantics remain consistent.

### Tests

-   Intersection commutativity, associativity, idempotence.
-   Union commutativity, associativity, idempotence.
-   Duplicate and nested component elimination.
-   Required incompatible field yields `never`.
-   Optional incompatible field yields optional-never rather than
    whole-object `never`.
-   Literal field intersections: `int & 1 => 1`, `1 & 2 => never`.
-   Deterministic type identity regardless of input order.
-   Recursive type normalization terminates.

### Acceptance criteria

-   Equivalent expressions normalize to one canonical type identity.
-   No parent-order-dependent result.
-   All simplifications preserve the specified field presence semantics.

------------------------------------------------------------------------

## 5. Phase 3 --- Structural subtyping and member access

### Tasks

1.  Implement structural object subtyping for open object types.
2.  Require every required target field to be provided by the source.
3.  Validate optional target fields when present in the source.
4.  Ensure field value type compatibility is sound for writable
    locations.
5.  Integrate structural subtype queries into the central type-relation
    service, not scattered ad hoc checks.
6.  Integrate the relation into:
    -   variable initialization and assignment;
    -   function arguments and receiver arguments;
    -   return values;
    -   overload candidate applicability and specificity;
    -   generic bounds/constraints;
    -   container element types where existing type relations are used.
7.  Implement union common-member lookup:
    -   member must be available on every alternative;
    -   read type must be safe across alternatives;
    -   write must be valid for every possible alternative.
8.  Keep exact/candidate/unknown actual-type analysis separate from
    structural declared-type compatibility.

### Tests

-   Width subtyping with extra fields.
-   Missing required field rejection.
-   Optional target field accepted when absent.
-   Optional target field validated when present.
-   Structural subtype in assignment, argument, return, receiver,
    generic bound, and overload cases.
-   Overload specificity deterministic for multiple structurally
    applicable candidates.
-   Union common field read.
-   Union-only-on-one-branch field rejected.
-   Union writes validated against every alternative.
-   No unsound write through a widened or narrowed view.

### Acceptance criteria

-   Structural compatibility behaves consistently across all
    type-consuming operations.
-   No individual call site reimplements a divergent
    object-compatibility algorithm.

------------------------------------------------------------------------

## 6. Phase 4 --- Inheritance as intersection plus declaration validation

### Tasks

1.  Resolve each parent type and local field declaration.
2.  Build the semantic type expression:
    `Parent1 & ... & ParentN & LocalObjectType`.
3.  Before canonicalization, validate inheritance-specific member-kind
    conflicts using original declaration provenance.
4.  Reject same-name ordinary-var versus const declarations across
    inherited parents in either parent order.
5.  Keep this stricter rule exclusive to inheritance declarations; plain
    intersection continues to compute the type intersection.
6.  Merge compatible same-name fields into one canonical logical field.
7.  Flatten inherited fields into the final data layout; do not allocate
    separate parent subobjects or duplicate same-name storage.
8.  Deduplicate the same originating field reached through diamond
    paths.
9.  Ensure parent declaration order does not alter:
    -   validity;
    -   resulting type;
    -   field identity;
    -   layout;
    -   diagnostics ordering where deterministic output is expected.
10. Remove or adapt legacy inherited-field lookup/layout logic that
    conflicts with flattened canonical fields.
11. Ensure inherited methods are not copied or included in data
    composition.
12. Keep nominal ancestry metadata separately from structural type
    identity. It is needed for nominal checks and parent-qualified
    factory syntax, but does not define structural compatibility.

### Tests

-   `A & B` and `data C : A, B` produce equivalent normalized data
    structure when declaration checks pass.
-   Same-name same-kind same-type fields merge to one field.
-   Same-name fields with intersecting types produce the expected
    narrowed field type.
-   Required incompatible field yields `never` for plain intersection
    and a declaration error where inheritance validity requires a
    constructible data template.
-   Var/const conflict rejected in both parent orders.
-   Same-source diamond field appears once.
-   Distinct compatible same-name fields produce one logical field and
    one storage slot.
-   No inherited function becomes a member implementation of the child.
-   Nominal ancestry remains queryable even though type structure is
    flattened.

### Acceptance criteria

-   Inheritance uses the shared intersection normalizer for data
    structure.
-   Inheritance-only checks are explicit and run before provenance is
    discarded.
-   Final field layout is flattened and deterministic.

------------------------------------------------------------------------

## 7. Phase 5 --- Function receiver semantics and `new` declaration model

### Tasks

1.  Confirm and document the existing semantic model for template-body
    functions as receiver-bearing free functions.
2.  Ensure subtype relationships do not copy or inherit these functions.
3.  Ensure a subtype receiver can call a parent-declared function
    through ordinary structural receiver compatibility and ordinary
    function lookup rules.
4.  Add dedicated parser/AST/semantic representation for `new`
    declarations:
    -   no `func` keyword;
    -   no explicit return type;
    -   no implicit `this`;
    -   return type is the complete current `Self`;
    -   template generic parameters are in scope.
5.  Lower `new` to a normal function representation with a
    template-associated name/owner and return type `Self`.
6.  Ensure `new` participates in ordinary overload resolution and
    function call semantics.
7.  Do not retain constructor-specific field assignment completeness
    checks.
8.  Remove old constructor/init semantics only after all uses and
    compatibility/migration paths are accounted for.

### Tests

-   Template-body function is callable through receiver binding.
-   Child does not acquire a copied parent function declaration.
-   Child value can call a parent receiver function when structurally
    compatible and lookup rules expose it.
-   `new` declaration has inferred `Self` return type.
-   Generic `new` sees enclosing template type parameters.
-   `new` overloads use ordinary overload resolution.
-   No constructor-specific all-fields-assigned diagnostic is emitted.

### Acceptance criteria

-   There is one function implementation/call-resolution path for
    ordinary functions and `new`.
-   No implicit method inheritance is introduced.
-   No legacy constructor semantic remains reachable after migration.

------------------------------------------------------------------------

## 8. Phase 6 --- Object literals and `new` factory execution

### Tasks

1.  Implement or extend object literal expression parsing and semantic
    analysis.
2.  Infer object literal type from its field entries.
3.  Implement shorthand `{ i }` as lexical lookup of `i`, equivalent to
    `{ i: i }`.
4.  Make shorthand available in all object literal contexts, not only
    `new`.
5.  Validate duplicate field names according to a deterministic rule; do
    not silently pick one without an explicit language rule.
6.  Ensure object literal result is a value with its own appropriate
    data payload/location semantics.
7.  Implement `new` body return checking:
    -   return expression must exist on reachable successful paths;
    -   return expression type must be a subtype of `Self`;
    -   open-object extra fields are accepted;
    -   missing required fields are rejected by ordinary structural
        subtyping.
8.  Lower `A(args...)` to ordinary resolution of `A.new(args...)`.
9.  Ensure `A()` does not trigger automatic parent factory calls.
10. Ensure a factory's returned value is not silently rewritten as
    mutation of a preallocated uninitialized `Self`.
11. Preserve normal function effect, value, and storage semantics.

### Tests

-   `{ i }` shorthand lexical binding.
-   `{ x: expr }` explicit property value.
-   Object literal type inference.
-   Extra fields accepted.
-   Missing required fields rejected.
-   Wrong field type rejected.
-   `new` returning a structural subtype of Self accepted.
-   `new` returning incompatible value rejected.
-   `A()` resolves to `A.new()`.
-   Explicit overload selection works.
-   No implicit parent `new` call.
-   Multiple return paths checked.
-   Factory return value has correct independent/shared location
    semantics.

### Acceptance criteria

-   Object creation is a factory call returning a complete value.
-   All field completeness checks arise from ordinary type
    compatibility, not special constructor analysis.

------------------------------------------------------------------------

## 9. Phase 7 --- Implicit factory generation from field initializers

### Tasks

1.  Generate an implicit zero-argument `new` when field initializers
    provide a valid object literal for the template's required fields.
2.  Generate its body conceptually as lexical field locals followed by
    an object literal using shorthand.
3.  Do not include inherited field initializers in the child template's
    implicit factory.
4.  Do not automatically call any parent factory.
5.  Explicit same-signature `new` suppresses/replaces the corresponding
    implicit generated overload.
6.  Other explicit overloads coexist with remaining implicit overloads.
7.  If the template has required fields without initializers, do not
    generate an invalid default factory.
8.  If the template has no fields, generate a default factory only if
    the language's object literal semantics define a valid empty object
    satisfying Self.
9.  Keep generated factories deterministic and stable across
    compilation/cache/serialization.
10. Ensure generated factories use the same normal function pipeline as
    explicit `new`.

### Tests

-   All fields initialized =\> implicit `new()` generated.
-   Required field without initializer =\> no invalid implicit `new()`.
-   Optional field without initializer can be omitted where object
    literal rules permit.
-   Explicit zero-argument `new` replaces implicit zero-argument
    version.
-   Explicit parameterized `new` coexists with implicit zero-argument
    version.
-   Child implicit `new` initializes only child-local initialized fields
    and does not call parent factories.
-   Generated factory returns Self.
-   Recompilation/cache serialization does not duplicate generated
    factories.

### Acceptance criteria

-   Field initializers are only a source-code convenience for generating
    a factory.
-   There is no hidden field initialization phase separate from the
    generated factory body.

------------------------------------------------------------------------

## 10. Phase 8 --- Parent-qualified factory invocation

### Tasks

1.  Confirm exact receiver-bound syntax against parser conventions
    before implementation. The semantic intent is a form such as
    `this.Parent.new(args...)`; parentheses in earlier notation
    `this.(Parent)` were explanatory, not literal syntax.
2.  Implement receiver-bound parent factory lookup only in contexts with
    a valid `this`, specifically member functions and extension
    functions.
3.  Require `Parent` to be a nominal ancestor of the static receiver
    type. Structural compatibility alone must not qualify.
4.  Bind current `this` as the receiver argument.
5.  Implement explicit form `Parent.new(this, args...)` as an ordinary
    free-function call.
6.  Permit explicit form wherever ordinary function calls are permitted.
7.  Explicit form requires ordinary receiver argument type compatibility
    only; no nominal ancestry requirement.
8.  Both forms use ordinary overload resolution and effects after
    receiver binding.

### Tests

-   Direct nominal parent accepted.
-   Indirect nominal ancestor accepted.
-   Structurally compatible but unrelated type rejected by
    receiver-bound syntax.
-   Receiver-bound syntax rejected without `this`.
-   Receiver-bound syntax accepted in member and extension functions.
-   Explicit free-function form accepted for structurally compatible
    receiver.
-   Explicit form accepted outside member/extension contexts.
-   Overload resolution is identical after receiver binding.

### Acceptance criteria

-   Syntax forms differ only in receiver-binding and nominal-ancestry
    constraints.
-   No constructor-specific behavior is introduced.

------------------------------------------------------------------------

## 11. Phase 9 --- Optional object spread (deferred)

Do not start this phase until phases 1--8 are stable.

### Proposed syntax

``` mcfpp
new() {
    var position = Position.new()
    var health = 100
    return {
        ...position,
        health
    }
}
```

### Future tasks

-   Define spread evaluation order.
-   Define duplicate-field behavior and whether explicit fields override
    spread fields.
-   Infer resulting object type.
-   Validate spread fields against Self.
-   Preserve correct value-copy/reference semantics.
-   Ensure spread does not accidentally duplicate or alias storage
    unless explicitly intended.

### Acceptance criteria

-   Spread semantics are documented before implementation.
-   Duplicate handling is deterministic.
-   Resulting object is checked through ordinary structural subtype
    rules.

------------------------------------------------------------------------

## 12. Cross-cutting correctness requirements

### 12.1 Storage and aliasing

-   Structural views and intersections must not create extra runtime
    storage by themselves.
-   Flattened fields map to one logical field and one storage path per
    resulting object layout.
-   `as` views continue to share the source `Place` where existing
    semantics require it.
-   Ordinary value assignment creates an independent destination
    according to existing copy rules.
-   Writes through any view must invalidate overlapping facts/caches and
    respect the effective field contract.
-   A permissive `as` cannot bypass const or actual storage constraints.

### 12.2 Compile-time facts and constants

-   Literal values remain immutable compiler values.
-   No constant payload may retain a mutable `Var` or NBT tag reference.
-   Structural normalization must not turn runtime/unknown values into
    compile-time constants.
-   `new` return checking must use the established value/type
    information model.

### 12.3 Generics

-   Generic object types preserve instantiated type arguments.
-   Structural comparison recurses through field types using established
    variance/type relation rules.
-   `Self` in a generic template factory is the fully instantiated
    template type.
-   Do not accidentally specialize ordinary functions based on
    incidental argument constants.
-   Preserve existing readonly compile-time generic parameter semantics.

### 12.4 Diagnostics

-   Report inheritance var/const conflicts with both declaration
    origins.
-   Report required-field incompatibility with field path and source
    types.
-   Report missing required fields in factory return values.
-   Distinguish optional-never from required-never.
-   Avoid reporting the same underlying conflict multiple times during
    normalization.
-   Keep parent-order-independent diagnostics deterministic.

### 12.5 Serialization and caches

-   Update type serialization/deserialization for object types, literal
    types, intersections, unions, and generated factories as needed.
-   Canonical type identity must survive cache round trips.
-   Generated implicit factories must not be duplicated after
    deserialization/recompilation.
-   Invalidate stale caches when the semantic representation changes.

------------------------------------------------------------------------

## 13. Final validation matrix

Before declaring the work complete, verify all of the following:

  -----------------------------------------------------------------------
  Area                                Required result
  ----------------------------------- -----------------------------------
  Object type                         Open structural object type with
                                      canonical identity

  Literal type                        Const values carry literal types

  Optional field                      Represents absence, not null value

  Intersection                        Canonical, associative,
                                      commutative, idempotent

  Required conflict                   Produces `never`

  Optional conflict                   Produces optional-never

  Union                               Canonical and exposes only
                                      guaranteed common members

  Structural subtype                  Used consistently across
                                      type-consuming operations

  Inheritance                         Parent intersections plus stricter
                                      declaration checks

  Var/const inherited conflict        Error regardless of parent order

  Field layout                        Flattened, one logical
                                      field/storage slot

  Methods                             Never copied/inherited through data
                                      inheritance

  `as`                                No structural compatibility
                                      enforcement

  `new`                               Ordinary factory function returning
                                      Self

  `A()`                               Resolves to `A.new()`

  Implicit factory                    Generated from field initializers

  Explicit factory                    Same-signature implicit factory
                                      suppressed

  Object literal                      Supports lexical shorthand `{ i }`

  Factory return                      Checked by ordinary
                                      `ReturnType <: Self`

  Parent factory                      Never automatically invoked

  Parent-qualified call               Nominal rule for receiver-bound
                                      form; ordinary compatibility for
                                      explicit form

  Object spread                       Deferred until core behavior is
                                      stable
  -----------------------------------------------------------------------

## 14. Completion criteria

The implementation is complete when:

1.  All phases 1--8 are implemented and documented.
2.  Focused tests for every listed semantic rule pass.
3.  Full project tests pass on the repository's supported JDK/toolchain.
4.  No old constructor/init-specific field completeness path remains
    active.
5.  No inherited function-copying or parent-constructor-chain behavior
    remains active.
6.  Type normalization and layout are deterministic regardless of
    parent/intersection operand order.
7.  Serialization/cache round trips preserve the new semantic model.
8.  User-facing documentation and migration notes describe the new
    syntax and behavior.
9.  Any deferred behavior, especially object spread, is clearly
    documented as not yet implemented.

## 15. Open questions requiring explicit approval

Do not silently decide these during implementation:

1.  **Exact receiver-bound syntax:** confirm the parser spelling for the
    nominal-parent form (semantic target: `this.Parent.new(...)`).
2.  **Duplicate object-literal keys:** decide whether duplicate keys are
    errors or have a defined overwrite order.
3.  **Optional-never representation:** confirm the existing type model
    can represent an optional field whose present value is impossible;
    otherwise add the smallest internal representation needed.
4.  **Read/write field contracts:** confirm the exact write-safety rule
    for structural views and union common members, especially where the
    current compiler has separate declared and actual storage
    constraints.
5.  **Empty object factory:** confirm whether a fieldless template
    receives an implicit zero-argument factory.
6.  **Factory control flow:** confirm whether every reachable successful
    path must return explicitly, or whether a final object expression is
    permitted.
7.  **Object spread:** intentionally deferred; do not implement without
    a separate approved specification.

------------------------------------------------------------------------

## Appendix A --- Compact semantic examples

### A. Inheritance expands to an intersection

``` mcfpp
data Position {
    var x: int
    var y: int
}

data Named {
    var name: string
}

data Entity : Position, Named {
    var health: float
}
```

Equivalent data contract:

``` text
{ x: int, y: int, name: string, health: float }
```

No parent functions or factories are inherited or automatically invoked.

### B. Inheritance-only var/const conflict

``` mcfpp
data A {
    var x: int
}

data B {
    const x = 1
}

data C : A, B {} // declaration error, independent of parent order
```

Plain intersection remains valid and narrows the field:

``` text
{x: int} & {x: 1} = {x: 1}
```

### C. Explicit factory

``` mcfpp
data A {
    var i: int
}

new() {
    var i = 1
    return { i }
}
```

### D. Implicit factory from field initializers

``` mcfpp
data A {
    var x: int = 1
    var y: int = 2
}
```

Conceptually synthesizes:

``` mcfpp
new() {
    var x = 1
    var y = 2
    return { x, y }
}
```

### E. Construction call

``` mcfpp
var a: A = A()
```

Resolves to the ordinary overload selected from `A.new()`.

### F. Parent factory calls

Receiver-bound nominal form (exact syntax to confirm):

``` mcfpp
this.Parent.new(args...)
```

Explicit ordinary call:

``` mcfpp
Parent.new(this, args...)
```

The first requires nominal ancestry and a valid `this` context. The
second requires only ordinary function-call compatibility.
