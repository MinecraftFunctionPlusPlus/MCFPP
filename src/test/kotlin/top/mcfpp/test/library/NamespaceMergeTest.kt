package top.mcfpp.test.library

import top.mcfpp.Project
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class NamespaceMergeTest {
    @Test
    fun sharedNamespaceRetainsEnumsAndAliasesFromBothLibraries() {
        val namespace = Namespace("fixture.merge")
        val firstEnum = Enum("First", namespace.identifier)
        val secondEnum = Enum("Second", namespace.identifier)
        try {
            namespace.scope.addEnum(firstEnum.identifier, firstEnum)
            namespace.scope.putResolvedAlias("Count", MCFPPBaseType.Int)
            val other = Namespace(namespace.identifier).apply {
                scope.addEnum(secondEnum.identifier, secondEnum)
                scope.putResolvedAlias("Ratio", MCFPPBaseType.Float)
            }

            namespace.merge(other)

            assertSame(firstEnum, namespace.scope.getEnum("First"))
            assertSame(secondEnum, namespace.scope.getEnum("Second"))
            assertEquals(MCFPPBaseType.Int, namespace.scope.getType("Count"))
            assertEquals(MCFPPBaseType.Float, namespace.scope.getType("Ratio"))
        } finally {
            GlobalScope.scoreboards.remove(firstEnum.namespaceID)
            GlobalScope.scoreboards.remove(secondEnum.namespaceID)
        }
    }

    @Test
    fun conflictingDeclarationsKeepOriginalUntilForceIsRequested() {
        val namespace = Namespace("fixture.merge.conflict")
        val original = Enum("Mode", namespace.identifier)
        val replacement = Enum("Mode", namespace.identifier)
        val previousErrors = Project.errorCount
        try {
            namespace.scope.addEnum(original.identifier, original)
            namespace.scope.putResolvedAlias("Number", MCFPPBaseType.Int)
            val other = Namespace(namespace.identifier).apply {
                scope.addEnum(replacement.identifier, replacement)
                scope.putResolvedAlias("Number", MCFPPBaseType.Float)
            }

            namespace.merge(other)

            assertSame(original, namespace.scope.getEnum("Mode"))
            assertEquals(MCFPPBaseType.Int, namespace.scope.getType("Number"))
            assertEquals(previousErrors + 2, Project.errorCount)

            namespace.merge(other, force = true)

            assertSame(replacement, namespace.scope.getEnum("Mode"))
            assertEquals(MCFPPBaseType.Float, namespace.scope.getType("Number"))
            assertEquals(previousErrors + 2, Project.errorCount)
        } finally {
            Project.errorCount = previousErrors
            GlobalScope.scoreboards.remove(original.namespaceID)
        }
    }
}
