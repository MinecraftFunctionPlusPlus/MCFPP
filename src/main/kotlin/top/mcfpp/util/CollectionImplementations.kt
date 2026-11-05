package top.mcfpp.util

/**
 * Exact collection shapes known to be immutable, discovered through their public factories.
 * JDK and TeaVM use different private implementation classes, including separate one/two/many
 * element shapes. Do not infer immutability from an interface or probe it by attempting a write:
 * rollback journals must still reject unknown containers and borrowed unmodifiable views.
 */
internal object CollectionImplementations {
    val fixedList: Class<*> = java.util.Arrays.asList(1).javaClass
    val basicImmutableLists: Set<Class<*>> = setOf(
        emptyList<Any>().javaClass, java.util.Collections.emptyList<Any>().javaClass,
        java.util.Collections.singletonList(1).javaClass,
    )
    val immutableLists: Set<Class<*>> = basicImmutableLists + setOf(
        java.util.List.of<Int>().javaClass, java.util.List.of(1).javaClass,
        java.util.List.of(1, 2).javaClass, java.util.List.of(1, 2, 3).javaClass,
    )
    val immutableSets: Set<Class<*>> = setOf(
        emptySet<Any>().javaClass, java.util.Collections.emptySet<Any>().javaClass,
        java.util.Collections.singleton(1).javaClass, java.util.Set.of<Int>().javaClass,
        java.util.Set.of(1).javaClass, java.util.Set.of(1, 2).javaClass, java.util.Set.of(1, 2, 3).javaClass,
    )
    val basicImmutableMaps: Set<Class<*>> = setOf(
        emptyMap<Any, Any>().javaClass, java.util.Collections.emptyMap<Any, Any>().javaClass,
        java.util.Collections.singletonMap(1, 2).javaClass,
    )
    val immutableMaps: Set<Class<*>> = basicImmutableMaps + setOf(
        java.util.Map.of<Int, Int>().javaClass, java.util.Map.of(1, 2).javaClass,
        java.util.Map.of(1, 2, 3, 4).javaClass, java.util.Map.of(1, 2, 3, 4, 5, 6).javaClass,
    )
    val immutableListNames: Set<String> = immutableLists.mapTo(hashSetOf()) { it.name }
    val immutableMapNames: Set<String> = immutableMaps.mapTo(hashSetOf()) { it.name }
    val immutableSetNames: Set<String> = immutableSets.mapTo(hashSetOf()) { it.name }
}
