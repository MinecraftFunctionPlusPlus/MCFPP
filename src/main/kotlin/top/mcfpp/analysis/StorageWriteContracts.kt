package top.mcfpp.analysis

import top.mcfpp.core.lang.Var
import top.mcfpp.type.*

/** Contracts belong to the location, independently of the adapter used to reach it. */
internal object StorageWriteContracts {
    /** A copied mutable record owns the destination's field contracts, including optionality. */
    fun copiedType(target: Var<*>, producer: MCFPPType): MCFPPType {
        if (producer !is MCFPPObjectType || target.isConst || target.symbol?.mutable == false ||
            StorageAccess.ensure(target).view != null) return producer
        val destination = TypeCompositions.objectShape(target.type)
        val result = MCFPPObjectType(producer.fields.map { source ->
            val field = destination?.fieldsByName?.get(source.name)
            val fixed = source.kind == ObjectMemberKind.CONST
            ObjectFieldContract(source.name,
                if (fixed) source.type else field?.type?.takeIf { source.type.isSubOf(it) } ?: TypeCompositions.widen(source.type),
                field?.optional ?: source.optional,
                if (fixed || field?.kind == ObjectMemberKind.CONST) ObjectMemberKind.CONST else ObjectMemberKind.VAR,
                source.origins, field?.readable ?: source.readable,
                !fixed && (field?.writable ?: source.writable))
        })
        return if (target.type is MCFPPUnionType && !result.isSubOf(target.type)) producer else result
    }

    fun accepts(value: Var<*>, target: MCFPPType): Boolean {
        if (target is MCFPPLiteralType) {
            val primitive = (value.type as? MCFPPLiteralType)?.primitive ?: value.type
            if (!primitive.isSubOf(target.primitive)) return false
            var payload = StorageAccess.snapshot(value) ?: return false
            while (payload is CompilerValue.Typed) payload = payload.payload
            return payload == target.value
        }
        return top.mcfpp.model.function.ParameterMatcher.accepts(value, target) && knownOptionalCompatible(value, target)
    }

    /** Open source contracts can hide extra fields; present producer fields still keep their types. */
    private fun knownOptionalCompatible(value: Var<*>, target: MCFPPType): Boolean {
        if (target is MCFPPUnionType) {
            val applicable = target.types.filter { top.mcfpp.model.function.ParameterMatcher.accepts(value, it) }
            return applicable.isEmpty() || applicable.any { knownOptionalCompatible(value, it) }
        }
        val required = TypeCompositions.objectShape(target) ?: return true
        val declared = TypeCompositions.objectShape(value.type) ?: return true
        val hidden = required.fields.filter { it.optional && it.name !in declared.fieldsByName }
        if (hidden.isEmpty()) return true
        val binding = value.storageBinding ?: return true
        val identities = when (val known = binding.data.facts.read(binding.place)?.type) {
            is TypeKnowledge.Exact -> listOf(known.type)
            is TypeKnowledge.Candidates -> known.types.toList()
            else -> emptyList()
        }
        for (field in hidden) {
            val presence = binding.data.facts.read(binding.place.field(field.name))
            if (presence?.presence == Presence.Absent || presence?.state == ValueState.UNINITIALIZED) continue
            for (identity in identities) {
                val actual = binding.data.types[identity]?.let(TypeCompositions::objectShape)?.fieldsByName?.get(field.name) ?: continue
                if (!actual.type.isSubOf(field.type)) return false
            }
        }
        return true
    }

    fun requirements(target: Var<*>): List<MCFPPType> = requirements(target, StorageAccess.ensure(target).place)

    private fun requirements(target: Var<*>, place: Place): List<MCFPPType> {
        val binding = StorageAccess.ensure(target)
        val data = binding.data
        val result = arrayListOf<MCFPPType>()
        fun add(type: MCFPPType, path: List<PathSegment>) {
            result += project(type, path).map { contract ->
                // A type wrapper's private delegate shares its physical root with the nominal
                // value. Writes through that exact delegate obey its backing type contract.
                if (binding.view != null && contract is MCFPPTypeDataTemplateType && contract.typeAs == target.type)
                    contract.typeAs else contract
            }
        }
        if (place.path.take(binding.place.path.size) == binding.place.path)
            add(target.type, place.path.drop(binding.place.path.size))
        for ((ancestor, declaration) in data.declarations) {
            if (!ancestorOf(ancestor, place)) continue
            val type = data.types[declaration.declaredType] ?: MCFPPType.resolveTypeId(declaration.declaredType) ?: continue
            add(type, place.path.drop(ancestor.path.size))
        }
        for ((ancestor, fact) in data.facts.entries()) {
            if (!ancestorOf(ancestor, place)) continue
            // A scalar's present value is evidence, not a permanent mutable field contract.
            // The producer's containing object does define the shared field's contract.
            if (ancestor == place && binding.place == place && binding.view == null) continue
            val alternatives = when (val known = fact.type) {
                is TypeKnowledge.Exact -> listOf(known.type)
                is TypeKnowledge.Candidates -> known.types.toList()
                TypeKnowledge.Unknown -> emptyList()
            }
            for (identity in alternatives) {
                val type = data.types[identity] ?: MCFPPType.resolveTypeId(identity) ?: continue
                val storageType = TypeCompositions.widen(type)
                add(storageType, place.path.drop(ancestor.path.size))
            }
        }
        return result.distinctBy { it.typeId }
    }

    fun rejection(target: Var<*>, value: Var<*>): MCFPPType? = requirements(target).firstOrNull { !accepts(value, it) }

    fun accessRejection(target: Var<*>, write: Boolean): String? =
        accessRejection(target, (target.storageBinding ?: StorageAccess.ensure(target)).place, write)

    private fun accessRejection(target: Var<*>, place: Place, write: Boolean): String? {
        val binding = target.storageBinding ?: StorageAccess.ensure(target)
        val backing = top.mcfpp.model.function.Function.currFunction.scope.getVar("field")?.storageBinding
            ?.takeIf { it.data === binding.data }
        fun inspect(type: MCFPPType, owner: Place, path: List<PathSegment>): String? {
            if (path.isEmpty()) return null
            if (type is MCFPPUnionType) return type.types.firstNotNullOfOrNull { inspect(it, owner, path) }
            val head = path.first()
            val tail = path.drop(1)
            if (head is PathSegment.Field) {
                val field = TypeCompositions.objectShape(type)?.fieldsByName?.get(head.name) ?: return null
                val child = owner.field(head.name)
                val backingAccess = child == backing?.place &&
                    top.mcfpp.model.function.Function.currFunction.accessorField == head.name
                if (!backingAccess && (!write || tail.isNotEmpty()) && !field.readable)
                    return "Field '${head.name}' cannot be read: its shared source property has no getter"
                if (!backingAccess && write && tail.isEmpty() && !field.writable)
                    return "Field '${head.name}' cannot be written: its shared source property has no setter"
                return inspect(field.type, child, tail)
            }
            val element = when (type) {
                is MCFPPListType -> type.generic.singleOrNull()
                is MCFPPImmutableListType -> type.generic.singleOrNull()
                else -> null
            } ?: return null
            return inspect(element, Place(owner.root, owner.path + head), tail)
        }
        fun inspect(type: MCFPPType, owner: Place): String? =
            if (ancestorOf(owner, place)) inspect(type, owner, place.path.drop(owner.path.size)) else null

        var parent = target.parent as? Var<*>
        while (parent != null) {
            val access = parent.storageBinding
            if (access?.data === binding.data) inspect(parent.type, access.place)?.let { return it }
            parent = parent.parent as? Var<*>
        }
        for ((owner, declaration) in binding.data.declarations) {
            if (!ancestorOf(owner, place)) continue
            val type = binding.data.types[declaration.declaredType] ?: continue
            inspect(type, owner)?.let { return it }
        }
        for ((owner, fact) in binding.data.facts.entries()) {
            if (!ancestorOf(owner, place)) continue
            val identities = when (val known = fact.type) {
                is TypeKnowledge.Exact -> listOf(known.type)
                is TypeKnowledge.Candidates -> known.types.toList()
                TypeKnowledge.Unknown -> emptyList()
            }
            for (identity in identities) binding.data.types[identity]?.let { inspect(it, owner) }?.let { return it }
        }
        return null
    }

    fun effectAccessRejection(target: Var<*>, effect: Effect): String? {
        val binding = StorageAccess.ensure(target)
        val places = when (effect) {
            is Effect.Writes -> effect.places.filter { it.overlaps(binding.place) }
            Effect.Unknown -> listOf(binding.place)
            else -> return null
        }
        fun contents(type: MCFPPType, place: Place, seen: Set<TypeId>): String? {
            if (type.typeId in seen) return null
            if (type is MCFPPUnionType) return type.types.firstNotNullOfOrNull { contents(it, place, seen + type.typeId) }
            for (field in TypeCompositions.objectShape(type)?.fields.orEmpty()) {
                val child = place.field(field.name)
                if (field.writable) accessRejection(target, child, true)?.let { return it }
                if (field.readable) contents(field.type, child, seen + type.typeId)?.let { return it }
            }
            return null
        }
        for (place in places) {
            accessRejection(target, place, true)?.let { return it }
            if (place == binding.place) contents(target.type, place, emptySet())?.let { return it }
        }
        return null
    }

    /** Calls with an unobserved payload must be safe for every value their view can write. */
    fun effectRejection(target: Var<*>, effect: Effect): MCFPPType? {
        val binding = StorageAccess.ensure(target)
        val places = when (effect) {
            is Effect.Writes -> effect.places.filter { it.overlaps(binding.place) }
            Effect.Unknown -> listOf(binding.place)
            else -> return null
        }
        for (place in places) {
            if (!ancestorOf(binding.place, place)) continue
            val written = project(target.type, place.path.drop(binding.place.path.size))
            for (requirement in requirements(target, place))
                if (written.any { !it.isSubOf(requirement) }) return requirement
        }
        return null
    }

    fun canRemove(target: Var<*>): Boolean {
        val binding = StorageAccess.ensure(target)
        val name = (binding.place.path.lastOrNull() as? PathSegment.Field)?.name ?: return true
        val owner = Place(binding.place.root, binding.place.path.dropLast(1))
        return requirements(target, owner).all { type ->
            fun optional(type: MCFPPType): Boolean = if (type is MCFPPUnionType) type.types.all(::optional)
                else TypeCompositions.objectShape(type)?.fieldsByName?.get(name)?.optional != false
            optional(type)
        }
    }

    private fun ancestorOf(ancestor: Place, place: Place): Boolean = ancestor.root == place.root &&
        place.path.take(ancestor.path.size) == ancestor.path

    private fun project(type: MCFPPType, path: List<PathSegment>): List<MCFPPType> {
        if (path.isEmpty()) return listOf(type)
        if (type is MCFPPUnionType) return type.types.flatMap { project(it, path) }
        val child = when (val segment = path.first()) {
            is PathSegment.Field -> TypeCompositions.objectShape(type)?.fieldsByName?.get(segment.name)?.type
                ?: (type as? MCFPPDictType)?.generic?.singleOrNull()
            is PathSegment.Index, PathSegment.UnknownIndex -> when (type) {
                is MCFPPListType -> type.generic.singleOrNull()
                is MCFPPImmutableListType -> type.generic.singleOrNull()
                else -> null
            }
        } ?: return emptyList()
        return project(child, path.drop(1))
    }
}
