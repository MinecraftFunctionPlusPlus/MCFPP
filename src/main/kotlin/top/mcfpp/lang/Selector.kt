package top.mcfpp.lang

import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.StringTag
import net.querz.nbt.tag.Tag
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPNBTType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.util.LogProcessor
import java.util.*


/**
 * 目标选择器（Target Selector）可在无需指定确切的玩家名称或UUID的情况下在命令中选择任意玩家与实体。目标选择器变量可以选择一个或多个实体，目标选择器参数可以根据特定条件筛选目标。
 *
 * 在mcfpp中你可以直接让目标选择器作为命令函数的参数，也可以让目标选择器选择实体，得到一个实体或者一个实体列表，之后再对它们进行操作
 *
 * 在构造一个目标选择器示例的时候，目标选择器的类型是必然确定的，因此只有用于构造确定变量的构造函数
 *
 * @see Entity
 */
class Selector : NBTBasedData<ListTag<StringTag>>() {
//
//    override var javaValue: ListTag<StringTag>? = null
//    override var type: MCFPPType = MCFPPBaseType.Selector
//
//    var text: String? = null
//
//    /**
//     * 目标选择器的类型
//     */
//    var selectorType: SelectorType = SelectorType.ALL_ENTITIES
//
//    /**
//     * 目标选择器的参数
//     */
//    var selectorArgs = HashMap<String, Any>()
//
//    /**
//     * 是否选择的单个实体
//     */
//    var singleEntity = false
//
//    /**
//     * 创建一个list类型的变量。它的mc名和变量所在的域容器有关。
//     *
//     * @param identifier 标识符。默认为
//     */
//    constructor(
//        curr: FieldContainer,
//        identifier: String = UUID.randomUUID().toString()
//    ) : this(curr.prefix + identifier) {
//        this.identifier = identifier
//    }
//
//    /**
//     * 创建一个list值。它的标识符和mc名相同。
//     * @param identifier identifier
//     */
//    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier)
//
//    /**
//     * 创建一个固定的list
//     *
//     * @param identifier 标识符
//     * @param curr 域容器
//     * @param type 类型
//     */
//    constructor(
//        curr: FieldContainer,
//        type: SelectorType,
//        identifier: String = UUID.randomUUID().toString()
//    ) : super(curr.prefix + identifier) {
//        isConcrete = true
//        val value = ListTag(StringTag::class.java)
//        value.add(StringTag("@${toSelectorTypeString(type)}"))
//        this.javaValue = value
//    }
//
//    /**
//     * 创建一个固定的list。它的标识符和mc名一致/
//     * @param identifier 标识符。如不指定，则为随机uuid
//     *
//     */
//    constructor(type: SelectorType, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
//        isConcrete = true
//        val value = ListTag(StringTag::class.java)
//        value.add(StringTag("@${toSelectorTypeString(type)}"))
//        this.javaValue = value
//    }
//
//    /**
//     * 复制一个list
//     * @param b 被复制的list值
//     */
//    constructor(b: Selector) : super(b)
//
//
//    /**
//     * 将b中的值赋值给此变量
//     * @param b 变量的对象
//     */
//    override fun assign(b: Var<*>?) {
//        if(b is Selector){
//            assignCommand(b)
//        }else{
//            throw VariableConverseException()
//        }
//    }
//
//    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
//        TODO()
//    }
//
//    override fun getMemberFunction(
//        key: String,
//        readOnlyParams: List<MCFPPType>,
//        normalParams: List<MCFPPType>,
//        accessModifier: Member.AccessModifier
//    ): Pair<Function, Boolean> {
//        TODO("Not yet implemented")
//    }
//
//    override fun cast(type: MCFPPType): Var<*> {
//        return when(type){
//            MCFPPBaseType.Selector -> this
//            MCFPPNBTType.NBT -> NBTBasedData(javaValue!!)
//            else -> throw VariableConverseException()
//        }
//    }
//
//    override fun createTempVar(): Var<*> = Selector()
//    override fun createTempVar(value: Tag<*>): Var<*> {
//        val re = Selector(this.selectorType)
//        re.javaValue = this.javaValue
//        re.selectorArgs = this.selectorArgs
//        return re
//    }
//
//    companion object {
//
//        val data = CompoundData("selector","mcfpp")
//
//        fun getSelectorType(char: Char): SelectorType{
//            return when(char){
//                'a' -> SelectorType.ALL_PLAYERS
//                'e' -> SelectorType.ALL_ENTITIES
//                'p' -> SelectorType.NEAREST_PLAYER
//                'r' -> SelectorType.RANDOM_PLAYER
//                's' -> SelectorType.SELF
//                else -> {
//                    LogProcessor.error("Invalid selector type: @$char")
//                    SelectorType.ALL_ENTITIES
//                }
//            }
//        }
//
//        fun toSelectorTypeString(type: SelectorType): Char{
//            return when(type){
//                SelectorType.ALL_PLAYERS -> 'a'
//                SelectorType.ALL_ENTITIES -> 'e'
//                SelectorType.NEAREST_PLAYER -> 'p'
//                SelectorType.RANDOM_PLAYER -> 'r'
//                SelectorType.SELF -> 's'
//            }
//        }
//
//        enum class SelectorType {
//            ALL_PLAYERS, ALL_ENTITIES, NEAREST_PLAYER, RANDOM_PLAYER, SELF
//        }
//    }
}


//TODO 对非编译确定量的支持
class SelectorConcrete : MCFPPValue<SelectorValue>, Var<SelectorValue>{

    override var value: SelectorValue

    /**
     * 创建一个固定的JavaVar
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: SelectorValue,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        this.value = value
    }

    /**
     * 创建一个固定的JavaVar。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: SelectorValue, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        this.value = value
    }

    override fun assign(b: Var<*>): Var<SelectorValue> {
        if(b is SelectorConcrete){
            this.value = b.value
        }else{
            LogProcessor.error("Cannot cast [${this.type}] to [$type]")
        }
        return this
    }

    override fun cast(type: MCFPPType): Var<*> {
        if (type == MCFPPBaseType.Selector) return this
        LogProcessor.error("Cannot cast [${this.type}] to [$type]")
        throw VariableConverseException()
    }

    override fun clone(): Var<*> = getTempVar()

    override fun getTempVar(): Var<*> = SelectorConcrete(value.clone(), identifier)

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        TODO("Not yet implemented")
    }

    companion object {
        val data = CompoundData("selector","mcfpp")

        init {
            data.parent.add(MCAny.data)
            data.field.addFunction(NativeFunction("x", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.Int, "x"), true)
            data.field.addFunction(NativeFunction("y", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.Int, "y"), true)
            data.field.addFunction(NativeFunction("z", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.Int, "z"), true)
            data.field.addFunction(NativeFunction("distance", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.Int, "distance"), true)
            data.field.addFunction(NativeFunction("dx", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.Int, "dx"), true)
            data.field.addFunction(NativeFunction("dy", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.Int, "dy"), true)
            data.field.addFunction(NativeFunction("dz", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.Int, "dz"), true)
            //TODO score
            data.field.addFunction(NativeFunction("tag", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.String, "tag"), true)
            data.field.addFunction(NativeFunction("tagNot", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.String, "tag"), true)
            data.field.addFunction(NativeFunction("team", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.String, "team"), true)
            data.field.addFunction(NativeFunction("teamNot", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.String, "team"), true)
            data.field.addFunction(NativeFunction("name", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.String, "name"), true)
            data.field.addFunction(NativeFunction("nameNot", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.String, "name"), true)
            data.field.addFunction(NativeFunction("type", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.String, "type"), true)
            data.field.addFunction(NativeFunction("typeNot", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.String, "type"), true)
            data.field.addFunction(NativeFunction("predicate", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.String, "predicate"), true)
            data.field.addFunction(NativeFunction("predicateNot", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.String, "predicate"), true)
            data.field.addFunction(NativeFunction("x_rotation", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.Int, "x_rotation"), true)
            data.field.addFunction(NativeFunction("y_rotation", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.Int, "y_rotation"), true)
            data.field.addFunction(NativeFunction("nbt", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPNBTType.NBT, "nbt"), true)
            data.field.addFunction(NativeFunction("level", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.Int, "level"), true)
            data.field.addFunction(NativeFunction("gamemode", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.String, "gamemode"), true)
            data.field.addFunction(NativeFunction("advancements", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.String, "advancement")
                .appendReadOnlyParam(MCFPPBaseType.Bool, "value"), true)
            data.field.addFunction(NativeFunction("limit", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.Int, "limit"), true)
            data.field.addFunction(NativeFunction("sort", SelectorConcreteData.INSTANCE , MCFPPBaseType.Selector,"mcfpp")
                .appendReadOnlyParam(MCFPPBaseType.String, "sort"), true)
        }

    }
}

class SelectorValue(var selectorType: SelectorType) {


    var x : Int? = null
    var y : Int? = null
    var z : Int? = null
    var distance : Range<Int>? = null
    var dx: Int? = null
    var dy: Int? = null
    var dz: Int? = null

    var scores: HashMap<SbObject, Range<*>> = HashMap()
    var tag: HashMap<String, Boolean> = HashMap()
    var team: HashMap<String, Boolean> = HashMap()
    var name: Pair<String, Boolean>? = null
    var type: Pair<String, Boolean>? = null
    var predicate: HashMap<String, Boolean> = HashMap()

    var x_rotation: Range<Int>? = null
    var y_rotation: Range<Int>? = null
    var nbt: Tag<*>? = null
    var level : Range<*>? = null
    var gamemode : Pair<String, Boolean>? = null
        set(value){
            if(value == null) {
                field = null
                return
            }
            if(!gamemodeValues.contains(value.first)){
                LogProcessor.error("Invalid gamemode value: ${value.first}")
            }else{
                field = value
            }
        }

    var advancements: HashMap<String, Boolean> = HashMap()

    var limit: Int? = null
    var sort: String? = null
        set(value) {
            if(!sortValues.contains(value)){
                LogProcessor.error("Invalid sort value: $value")
            }else{
                field = value
            }
        }

    fun onlyIncludingPlayers() : Boolean {
        if(selectorType == SelectorType.RANDOM_PLAYER || selectorType == SelectorType.NEAREST_PLAYER || selectorType == SelectorType.ALL_PLAYERS){
            return true
        }
        if(type?.first == "player" && type?.second == true){
            return true
        }
        return false
    }

    fun selectingSingleEntity(): Boolean{
        if(selectorType == SelectorType.NEAREST_ENTITY || selectorType == SelectorType.NEAREST_PLAYER || selectorType == SelectorType.SELF || selectorType == SelectorType.RANDOM_PLAYER){
            return true
        }
        if(limit == 1){
            return true
        }
        return false
    }

    fun clone(): SelectorValue{
        val re = SelectorValue(selectorType)
        re.x = x
        re.y = y
        re.z = z
        re.distance = distance?.clone()
        re.dx = dx
        re.dy = dy
        re.dz = dz
        re.scores = HashMap(scores)
        re.tag = HashMap(tag)
        re.team = HashMap(team)
        re.name = name
        re.type = type
        re.predicate = HashMap(predicate)
        re.x_rotation = x_rotation?.clone()
        re.y_rotation = y_rotation?.clone()
        re.nbt = nbt
        re.level = level?.clone()
        re.gamemode = gamemode
        re.advancements = HashMap(advancements)
        re.limit = limit
        re.sort = sort
        return re
    }

    companion object{

        val sortValues = arrayOf("nearest","furthest","random","arbitrary")
        val gamemodeValues = arrayOf("survival","creative","adventure","spectator")


        fun toSelectorTypeString(type: SelectorType): Char{
            return when(type){
                SelectorType.ALL_PLAYERS -> 'a'
                SelectorType.ALL_ENTITIES -> 'e'
                SelectorType.NEAREST_PLAYER -> 'p'
                SelectorType.RANDOM_PLAYER -> 'r'
                SelectorType.SELF -> 's'
                SelectorType.NEAREST_ENTITY -> 'n'
            }
        }

        fun fromSelectorTypeString(char: Char): SelectorType{
            return when(char){
                'a' -> SelectorType.ALL_PLAYERS
                'e' -> SelectorType.ALL_ENTITIES
                'p' -> SelectorType.NEAREST_PLAYER
                'r' -> SelectorType.RANDOM_PLAYER
                's' -> SelectorType.SELF
                'n' -> SelectorType.NEAREST_ENTITY
                else -> {
                    LogProcessor.error("Invalid selector type: @$char")
                    SelectorType.ALL_ENTITIES
                }
            }
        }

        enum class SelectorType {
            ALL_PLAYERS, ALL_ENTITIES, NEAREST_PLAYER, RANDOM_PLAYER, SELF, NEAREST_ENTITY
        }
    }
}