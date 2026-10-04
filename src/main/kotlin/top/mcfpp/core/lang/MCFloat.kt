package top.mcfpp.core.lang

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.FloatProviders
import top.mcfpp.lib.NBTPath
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.SbObject
import top.mcfpp.model.*
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

open class MCFloat : MCNumber<Float> {

    // Legacy XiaoDouMathLib components. In 26.3 the runtime value lives at nbtPath.
    var sign: MCInt
    var int0: MCInt
    var int1: MCInt
    var exp : MCInt

    /**
     * 创建一个float类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(curr: FieldContainer, identifier: String = TempPool.getVarIdentify()) : this(identifier){
        name = curr.prefix + identifier
        sign = MCInt(name).setObj(SbObject.MCS_float_sign) as MCInt
        int0 = MCInt(name).setObj(SbObject.MCS_float_int0) as MCInt
        int1 = MCInt(name).setObj(SbObject.MCS_float_int1) as MCInt
        exp = MCInt(name).setObj(SbObject.MCS_float_exp) as MCInt
    }

    /**
     * 创建一个float值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        sign = MCInt(name).setObj(SbObject.MCS_float_sign) as MCInt
        int0 = MCInt(name).setObj(SbObject.MCS_float_int0) as MCInt
        int1 = MCInt(name).setObj(SbObject.MCS_float_int1) as MCInt
        exp = MCInt(name).setObj(SbObject.MCS_float_exp) as MCInt
        if (FloatProviders.enabled) {
            nbtPath = NBTPath.getNormalStackPath(this)
        } else {
            Project.enableModulePackage("math.float", "stdlib")
        }
    }

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: MCFloat) : super(b){
        sign = MCInt(b.sign)
        int0 = MCInt(b.int0)
        int1 = MCInt(b.int1)
        exp = MCInt(b.exp)
        if (!FloatProviders.enabled) Project.enableModulePackage("math.float", "stdlib")
    }

    override var type: MCFPPType = MCFPPBaseType.Float

    /**
     * 将分数储存在临时实体中
     *
     */
    @InsertCommand
    open fun toTempEntity() : MCFloat{
        if (FloatProviders.enabled) return this
        Function.addCommand("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_exp} = ${exp.name} ${exp.sbObject}")
        Function.addCommand("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_sign} = ${sign.name} ${sign.sbObject}")
        Function.addCommand("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_int0} = ${int0.name} ${int0.sbObject}")
        Function.addCommand("scoreboard players operation $tempFloatEntityUUID ${SbObject.Math_float_int1} = ${int1.name} ${int1.sbObject}")
        return tempFloat
    }

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    @Override
    @Throws(VariableConverseException::class)
    override fun doAssignedBy(b: Var<*>) : MCFloat {
        return when (b) {
            is MCFloat -> {
                assignCommand(b)
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                this
            }
        }
    }

    /**
     * 赋值
     * @param a 值来源
     */
    @InsertCommand
    override fun assignCommand(a: MCNumber<*>) : MCFloat {
        if (FloatProviders.enabled) return FloatProviders.assign(this, a as MCFloat)
        return if(a is MCFloatConcrete){
            MCFloatConcrete(this, a.value)
        }else {
            //this = a
            val pwp = a as MCFloat
            if(isTemp){
                Function.addCommand("scoreboard players operation ${sign.name} ${sign.sbObject} = ${pwp.sign.name} ${pwp.sign.sbObject}")
                Function.addCommand("scoreboard players operation ${int0.name} ${int0.sbObject} = ${pwp.int0.name} ${pwp.int0.sbObject}")
                Function.addCommand("scoreboard players operation ${int1.name} ${int1.sbObject} = ${pwp.int1.name} ${pwp.int1.sbObject}")
                Function.addCommand("scoreboard players operation ${exp.name} ${exp.sbObject} = ${pwp.exp.name} ${pwp.exp.sbObject}")
            }else{
                Function.addCommand(Command.buildAll("execute store result", nbtPath.memberIndex("sign"), "int 1 run", Commands.sbPlayerOperation(sign, "=", pwp.sign)))
                Function.addCommand(Command.buildAll("execute store result", nbtPath.memberIndex("int0"), "int 1 run", Commands.sbPlayerOperation(int0, "=", pwp.int0)))
                Function.addCommand(Command.buildAll("execute store result", nbtPath.memberIndex("int1"), "int 1 run", Commands.sbPlayerOperation(int1, "=", pwp.int1)))
                Function.addCommand(Command.buildAll("execute store result", nbtPath.memberIndex("exp"), "int 1 run", Commands.sbPlayerOperation(exp, "=", pwp.exp)))
            }
            this
        }
    }

    /**
     * 加法
     * @param a 加数
     * @return 计算的结果
     */
    @InsertCommand
    override fun plus(a: Var<*>): Var<*> {
        if (FloatProviders.enabled) return FloatProviders.arithmetic(this, a as MCFloat, "+")
        //t = t + a
        if(!isTemp) return getTempVar().plus(a)
        if(a as MCFloat != tempFloat) a.toTempEntity()
        Function.addCommand("execute as $tempFloatEntityUUID run function math.float:hpo/float/_add")
        return this
    }

    /**
     * 减法
     * @param a 减数
     * @return 计算的结果
     */
    @InsertCommand
    override fun minus(a: Var<*>): Var<*> {
        if (FloatProviders.enabled) return FloatProviders.arithmetic(this, a as MCFloat, "-")
        //t = t - a
        if(!isTemp) return getTempVar().minus(a)
        if(a as MCFloat != tempFloat) a.toTempEntity()
        Function.addCommand("execute as $tempFloatEntityUUID run function math.float:hpo/float/_rmv")
        return this
    }

    /**
     * 乘法
     * @param a 乘数
     * @return 计算的结果
     */
    @InsertCommand
    override fun times(a: Var<*>): Var<*> {
        if (FloatProviders.enabled) return FloatProviders.arithmetic(this, a as MCFloat, "*")
        //t = t * a
        if(!isTemp) return getTempVar().times(a)
        if(a as MCFloat != tempFloat) a.toTempEntity()
        Function.addCommand("execute as $tempFloatEntityUUID run function math.float:hpo/float/_mult")
        return this
    }

    /**
     * 除法
     * @param a 除数
     * @return 计算的结果
     */
    @InsertCommand
    override fun div(a: Var<*>): Var<*> {
        if (FloatProviders.enabled) return FloatProviders.arithmetic(this, a as MCFloat, "/")
        //t = t - a
        if(!isTemp) return getTempVar().div(a)
        if(a as MCFloat != tempFloat) a.toTempEntity()
        Function.addCommand("execute as $tempFloatEntityUUID run function math.float:hpo/float/_div")
        return this
    }

    override fun rem(a: Var<*>): Var<*> {
        if (FloatProviders.enabled) return FloatProviders.arithmetic(this, a as MCFloat, "%")
        LogProcessor.error("Float remainder has no implementation for the legacy component backend")
        return MCFloat().apply { isError = true }
    }

    /**
     * 这个数是否大于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isBigger(a: Var<*>): Var<*> {
        if (FloatProviders.enabled) return FloatProviders.compare(this, a as MCFloat, ">")
        //re = t > a
        if(!isTemp) return getTempVar().isBigger(a)
        if(a as MCFloat != tempFloat) a.toTempEntity()
        val re = ScoreBool()
        Function.addCommand(
            "execute store result score ${re.name} ${re.boolObject} as $tempFloatEntityUUID " +
                    "run function math.float:hpo/float/_isbigger")
        return re
    }

    /**
     * 这个数是否小于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isSmaller(a: Var<*>): Var<*> {
        if (FloatProviders.enabled) return FloatProviders.compare(this, a as MCFloat, "<")
        //re = t < a
        if(!isTemp) return getTempVar().isSmaller(a)
        if(a as MCFloat != tempFloat) a.toTempEntity()
        val re = ScoreBool()
        Function.addCommand(
            "execute store result score ${re.name} ${re.boolObject} as $tempFloatEntityUUID " +
                    "run function math.float:hpo/float/_issmaller")
        return re
    }

    /**
     * 这个数是否小于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isSmallerOrEqual(a: Var<*>): Var<*> {
        if (FloatProviders.enabled) return FloatProviders.compare(this, a as MCFloat, "<=")
        //re = t <= a
        if(!isTemp) return getTempVar().isSmallerOrEqual(a)
        if(a as MCFloat != tempFloat) a.toTempEntity()
        val re = ScoreBool()
        Function.addCommand(
            "execute store result score ${re.name} ${re.boolObject} as $tempFloatEntityUUID " +
                    "run function math.float:hpo/float/_issmallerorequal")
        return re
    }

    /**
     * 这个数是否大于等于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isBiggerOrEqual(a: Var<*>): Var<*> {
        if (FloatProviders.enabled) return FloatProviders.compare(this, a as MCFloat, ">=")
        //re = t >= a
        if(!isTemp) return getTempVar().isBiggerOrEqual(a)
        if(a as MCFloat != tempFloat) a.toTempEntity()
        val re = ScoreBool()
        Function.addCommand(
            "execute store result score ${re.name} ${re.boolObject} as $tempFloatEntityUUID " +
                    "run function math.float:hpo/float/_isbiggerorequal")
        return re
    }

    /**
     * 这个数是否等于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isEqual(a: Var<*>): Var<*> {
        if (FloatProviders.enabled) return FloatProviders.compare(this, a as MCFloat, "==")
        //re = t == a
        if(!isTemp) return getTempVar().isEqual(a)
        if(a as MCFloat != tempFloat) a.toTempEntity()
        val re = ScoreBool()
        Function.addCommand(
            "execute store result score ${re.name} ${re.boolObject} as $tempFloatEntityUUID " +
                    "run function math.float:hpo/float/_equal")
        return re
    }

    /**
     * 这个数是否不等于a
     * @param a 右侧值
     * @return 计算结果
     */
    @InsertCommand
    override fun isNotEqual(a: Var<*>): Var<*> {
        if (FloatProviders.enabled) return FloatProviders.compare(this, a as MCFloat, "!=")
        //re = t != a
        if(!isTemp) return getTempVar().isNotEqual(a)
        if(a as MCFloat != tempFloat) a.toTempEntity()
        val re = ScoreBool()
        Function.addCommand(
            "execute store result score ${re.name} ${re.boolObject} as $tempFloatEntityUUID " +
                    "run function math.float:hpo/float/_notequal")
        return re
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    @InsertCommand
    override fun explicitCast(type: MCFPPType): Var<*> {
        if (type == MCFPPBaseType.Int) return top.mcfpp.backend.NumericConversions.convert(this, type)
        return super.explicitCast(type)
    }

    override fun canExplicitCast(type: MCFPPType): Boolean {
        return super.canExplicitCast(type) || type == MCFPPBaseType.Int
    }

    override fun clone(): MCFloat {
        return MCFloat(this)
    }

    @InsertCommand
    override fun getTempVar(): MCFloat {
        // Native operations always produce a fresh result, so an operand view is sufficient.
        if (FloatProviders.enabled) return MCFloat(this).apply { isTemp = true }
        Function.addCommand("scoreboard players operation float_exp int = ${exp.name} ${exp.sbObject}")
        Function.addCommand("scoreboard players operation float_int0 int = ${int0.name} ${int0.sbObject}")
        Function.addCommand("scoreboard players operation float_int1 int = ${int1.name} ${int1.sbObject}")
        Function.addCommand("scoreboard players operation float_sign int = ${sign.name} ${sign.sbObject}")
        return MCFloat(ssObj).apply { isTemp = true }
    }

    override fun storeToStack() {
        storageBinding?.let { it.data.materialize(); return }
        if (FloatProviders.enabled) return // The runtime value already lives in NBT.
        if (nbtPath.pathList.isEmpty()) nbtPath = NBTPath.getNormalStackPath(this)
        val parts = listOf("sign" to sign, "int0" to int0, "int1" to int1, "exp" to exp)
        if (this is MCFloatConcrete) {
            Function.addCommand(Commands.dataSetValue(nbtPath, legacyNBTEncoding()))
        } else {
            Function.addCommand(Commands.dataSetValue(nbtPath, top.mcfpp.nbt.tags.CompoundTag()))
            for ((key, score) in parts) Function.addCommand(Command.buildAll("execute store result",
                nbtPath.memberIndex(key), "int 1 run scoreboard players get ${score.name} ${score.sbObject}"))
        }
    }

    override fun getFromStack() {
        storageBinding?.let { binding ->
            if (!FloatProviders.enabled) {
                for ((key, score) in listOf("sign" to sign, "int0" to int0, "int1" to int1, "exp" to exp))
                    top.mcfpp.analysis.StorageAccess.restoreScore(
                        top.mcfpp.analysis.StorageAccess.adapter(top.mcfpp.type.MCFPPBaseType.Int, key, binding.field(key)),
                        score.name, score.sbObject.toString())
            }
            return
        }
        if (FloatProviders.enabled) return
        for ((key, score) in listOf("sign" to sign, "int0" to int0, "int1" to int1, "exp" to exp))
            Function.addCommand(Command("execute store result score ${score.name} ${score.sbObject} run")
                .build(Commands.dataGet(nbtPath.memberIndex(key))))
    }

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    companion object{

        const val tempFloatEntityUUID = "53aa19cc-a067-402b-8ba1-9328cc5fb6c1"
        const val tempFloatEntityUUIDNBT = "[I;1403656652,-1603846101,-1952345304,-866142527]"

        fun floatToMCFloat(float: Float): Array<Int>{
            //获取指数部分
            val sign = if (float < 0) -1 else if(float == 0f) 0 else 1
            val absFloat = float.absoluteValue
            val exponent = floor(log10(absFloat.toDouble())).toInt()
            val factor = 10.0.pow((8 - exponent - 1).toDouble()).toInt()
            val n =  exponent + 1
            val qwq = (absFloat * factor).toInt()
            return arrayOf(sign, qwq/10000, qwq%10000, n)
        }

        val ssObj = MCFloatConcrete(value = Float.NaN)

        val tempFloat : MCFloat = MCFloat()

        init {
            ssObj.sign = MCInt("float_sign").setObj(SbObject.Math_int) as MCInt
            ssObj.int0 = MCInt("float_int0").setObj(SbObject.Math_int) as MCInt
            ssObj.int1 = MCInt("float_int1").setObj(SbObject.Math_int) as MCInt
            ssObj.exp = MCInt("float_exp").setObj(SbObject.Math_int) as MCInt
            ssObj.isTemp = true
            tempFloat.sign = MCInt(tempFloatEntityUUID).setObj(SbObject.MCS_float_sign) as MCInt
            tempFloat.exp = MCInt(tempFloatEntityUUID).setObj(SbObject.MCS_float_exp) as MCInt
            tempFloat.int0 = MCInt(tempFloatEntityUUID).setObj(SbObject.MCS_float_int0) as MCInt
            tempFloat.int1 = MCInt(tempFloatEntityUUID).setObj(SbObject.MCS_float_int1) as MCInt
            tempFloat.isTemp = true
        }

        fun ssObjToVar(identifier: String = TempPool.getVarIdentify()) : MCFloat{
            val re = MCFloat(identifier)
            re.isTemp = true
            re.assignedBy(ssObj)
            return re
        }
    }
}

class MCFloatConcrete : MCFloat, MCFPPValue<Float> {

    fun legacyNBTEncoding(): top.mcfpp.nbt.tags.CompoundTag = top.mcfpp.nbt.tags.CompoundTag().apply {
        for ((key, score) in listOf("sign" to sign, "int0" to int0, "int1" to int1, "exp" to exp))
            put(key, top.mcfpp.nbt.tags.primitive.IntTag((score as MCIntConcrete).value))
    }

    @Suppress("MUST_BE_INITIALIZED_WARNING")
    override var value: Float
        set(value) {
            field = value
            setJavaValue(value)
        }

    /**
     * 创建一个固定的float
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(curr: FieldContainer, value: Float, identifier: String = TempPool.getVarIdentify()) : super(curr, identifier) {
        this.value = value
    }

    /**
     * 创建一个固定的float。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Float, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = value
    }

    constructor(float: MCFloat, value: Float): super(float){
        this.value = value
    }

    constructor(v: MCFloatConcrete) : super(v){
        this.value = v.value
    }

    override fun clone(): MCFloatConcrete {
        return MCFloatConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        if (storageBinding != null) {
            val re = top.mcfpp.analysis.StorageAccess.read(MCFloat(this).apply { isDynamic = true })
            if (replace) replacedBy(re)
            return re
        }
        val qwq = if (FloatProviders.enabled) FloatProviders.materialize(this) else MCFloat(this)
        // A concrete float can have dynamic components after an arithmetic operation.
        // Only constant components need to be written to their scoreboards.
        if (!FloatProviders.enabled) {
            listOf(sign, int0, int1, exp).filterIsInstance<MCIntConcrete>().forEach { it.toDynamic(false) }
        }
        if(replace){
            if(parentTemplate() != null){
                (parent as DataTemplateObject).instanceField.putVar(identifier, qwq, true)
            }else{
                Function.currFunction.scope.putVar(identifier, qwq, true)
            }
        }
        return qwq
    }

    /**
     * 设置值，并更新记分板
     *
     * @param value
     */
    private fun setJavaValue(value: Float){
        val qwq = floatToMCFloat(value)
        sign = MCIntConcrete(sign, qwq[0])
        int0 = MCIntConcrete(int0, qwq[1])
        int1 = MCIntConcrete(int1, qwq[2])
        exp = MCIntConcrete(exp, qwq[3])
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    /**
     * 将分数储存在临时实体中
     */
    @InsertCommand
    override fun toTempEntity() : MCFloat{
        if (FloatProviders.enabled) return this
        Function.addCommand("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_sign} ${(sign as MCIntConcrete).value}")
        Function.addCommand("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_int0} ${(int0 as MCIntConcrete).value}")
        Function.addCommand("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_int1} ${(int1 as MCIntConcrete).value}")
        Function.addCommand("scoreboard players set $tempFloatEntityUUID ${SbObject.Math_float_exp} ${(exp as MCIntConcrete).value}")
        return tempFloat
    }


    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     *
     * @return
     */
    @InsertCommand
    override fun getTempVar(): MCFloat {
        if (FloatProviders.enabled) return MCFloatConcrete(this).apply { isTemp = true }
        ssObj.value = value
        val qwq = floatToMCFloat(value)
        Function.addCommand("scoreboard players set float_sign int ${qwq[0]}")
        Function.addCommand("scoreboard players set float_int0 int ${qwq[1]}")
        Function.addCommand("scoreboard players set float_int1 int ${qwq[2]}")
        Function.addCommand("scoreboard players set float_exp int ${qwq[3]}")
        return MCFloat(ssObj).apply { isTemp = true }
    }

    // Legacy operations use the simulator until backend-equivalent folding is proven.
    @InsertCommand
    override fun plus(a: Var<*>): Var<*> =
        if (FloatProviders.enabled) FloatProviders.arithmetic(this, a as MCFloat, "+")
        else getTempVar().plus(a)

    @InsertCommand
    override fun minus(a: Var<*>): Var<*> =
        if (FloatProviders.enabled) FloatProviders.arithmetic(this, a as MCFloat, "-")
        else getTempVar().minus(a)

    @InsertCommand
    override fun times(a: Var<*>): Var<*> =
        if (FloatProviders.enabled) FloatProviders.arithmetic(this, a as MCFloat, "*")
        else getTempVar().times(a)

    @InsertCommand
    override fun div(a: Var<*>): Var<*> =
        if (FloatProviders.enabled) FloatProviders.arithmetic(this, a as MCFloat, "/")
        else getTempVar().div(a)

    @InsertCommand
    override fun rem(a: Var<*>): Var<*> =
        if (FloatProviders.enabled) FloatProviders.arithmetic(this, a as MCFloat, "%")
        else getTempVar().rem(a)

    @InsertCommand
    override fun isBigger(a: Var<*>): Var<*> =
        if (FloatProviders.enabled) FloatProviders.compare(this, a as MCFloat, ">")
        else getTempVar().isBigger(a)

    @InsertCommand
    override fun isSmaller(a: Var<*>): Var<*> =
        if (FloatProviders.enabled) FloatProviders.compare(this, a as MCFloat, "<")
        else getTempVar().isSmaller(a)

    @InsertCommand
    override fun isSmallerOrEqual(a: Var<*>): Var<*> =
        if (FloatProviders.enabled) FloatProviders.compare(this, a as MCFloat, "<=")
        else getTempVar().isSmallerOrEqual(a)

    @InsertCommand
    override fun isBiggerOrEqual(a: Var<*>): Var<*> =
        if (FloatProviders.enabled) FloatProviders.compare(this, a as MCFloat, ">=")
        else getTempVar().isBiggerOrEqual(a)

    @InsertCommand
    override fun isEqual(a: Var<*>): Var<*> =
        if (FloatProviders.enabled) FloatProviders.compare(this, a as MCFloat, "==")
        else getTempVar().isEqual(a)

    @InsertCommand
    override fun isNotEqual(a: Var<*>): Var<*> =
        if (FloatProviders.enabled) FloatProviders.compare(this, a as MCFloat, "!=")
        else getTempVar().isNotEqual(a)
}
