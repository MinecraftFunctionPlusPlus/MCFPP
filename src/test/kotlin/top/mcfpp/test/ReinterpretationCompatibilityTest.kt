package top.mcfpp.test

import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.model.Member
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.type.*
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertFalse

class ReinterpretationCompatibilityTest {
    private fun template(name: String, vararg fields: Pair<String, MCFPPType>) = DataTemplate(name, "shape").apply {
        for ((field, type) in fields) scope.putVar(field, type.buildUnConcrete(field))
    }
    private fun compatible(source: DataTemplate, target: DataTemplate) =
        assertIs<ReinterpretationCompatibility.Result.Compatible>(TypeRelations.checkReinterpretation(source.getType(), target.getType()))
    private fun unproven(source: DataTemplate, target: DataTemplate) =
        assertIs<ReinterpretationCompatibility.Result.Unproven>(TypeRelations.checkReinterpretation(source.getType(), target.getType()))

    @Test fun requiredFieldsAllowExtrasAndIgnoreStaticMembers() {
        val source = template("source", "count" to MCFPPBaseType.Int, "extra" to MCFPPBaseType.String)
        val target = template("target", "count" to MCFPPBaseType.Int)
        target.scope.putVar("staticMember", MCInt("staticMember").apply { isStatic = true })
        compatible(source, target)
        assertFalse(TypeRelations.isSubtype(source.getType(), target.getType()))
        unproven(target, source)
    }

    @Test fun absentOptionalFieldsAndRequiredPresenceAreIndependent() {
        val source = template("source", "count" to MCFPPBaseType.Int)
        val target = template("target", "count" to MCFPPBaseType.Int, "optional" to MCFPPBaseType.Int)
        target.scope.getVar("optional")!!.nullable = true
        compatible(source, target)
        source.scope.getVar("count")!!.nullable = true
        unproven(source, target)
        target.scope.getVar("count")!!.nullable = true
        compatible(source, target)
    }

    @Test fun writableFieldsAreInvariantAndReadonlyDoesNotUseNumericPromotion() {
        val source = template("source", "count" to MCFPPBaseType.Int)
        val target = template("target", "count" to MCFPPBaseType.Float)
        unproven(source, target)
        target.scope.getVar("count")!!.isConst = true
        unproven(source, target)
        val same = template("same", "count" to MCFPPBaseType.Int)
        source.scope.getVar("count")!!.isConst = true
        unproven(source, same)
        same.scope.getVar("count")!!.isConst = true
        compatible(source, same)
    }

    @Test fun recursiveReadonlyShapesTerminateAndNestedMissingFieldsFail() {
        val source = template("source")
        val target = template("target")
        source.scope.putVar("next", DataTemplateObject(source, "next").apply { isConst = true })
        target.scope.putVar("next", DataTemplateObject(target, "next").apply { isConst = true })
        compatible(source, target)
        target.scope.putVar("missing", MCInt("missing"))
        unproven(source, target)
    }

    @Test fun structureCannotSatisfyPrivateFieldsOrAbstractCapabilities() {
        val source = template("source", "count" to MCFPPBaseType.Int)
        val target = template("target", "count" to MCFPPBaseType.Int)
        source.scope.getVar("count")!!.accessModifier = Member.AccessModifier.PRIVATE
        unproven(source, target)
        source.scope.getVar("count")!!.accessModifier = Member.AccessModifier.PUBLIC
        target.scope.addFunction(Function("ability", context = null).apply { isAbstract = true }, false)
        unproven(source, target)
    }

    @Test fun nominalUpcastsAndAnyAreCompatibleButDowncastsNeedProof() {
        val parent = template("parent")
        val child = template("child").apply { extends(parent) }
        compatible(child, parent)
        unproven(parent, child)
        assertIs<ReinterpretationCompatibility.Result.Compatible>(TypeRelations.checkReinterpretation(MCFPPBaseType.Any, child.getType()))
        assertIs<ReinterpretationCompatibility.Result.Unproven>(TypeRelations.checkReinterpretation(MCFPPBaseType.Int, MCFPPBaseType.Float))
    }
}
