package top.mcfpp.lang

import net.querz.nbt.io.SNBTUtil
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.Function
import top.mcfpp.lib.Member
import java.util.*
import net.querz.nbt.tag.*
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lib.Class
import top.mcfpp.util.LogProcessor
import kotlin.collections.ArrayList


object MCFPPNBTType:MCFPPType("nbt",listOf(MCFPPBaseType.Any))

/**
 * 一个nbt数据
 *
 * @constructor Create empty Nbt
 */
class NBT : Var<Tag<*>>, Indexable<NBT>{

    var path = ArrayList<Var<*>>()

    override var javaValue : Tag<*>? = null

    var nbtType: NBTType = NBTType.ANY

    override var type: MCFPPType = MCFPPNBTType

    /**
     * 创建一个nbt类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        this.identifier = identifier
        path.add(MCString(identifier))
    }

    /**
     * 创建一个nbt值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
        isTemp = true
        path.add(MCString(identifier))
    }

    /**
     * 创建一个固定的int
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: Tag<*>,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        isConcrete = true
        this.javaValue = value
        path.add(MCString(identifier))
    }

    /**
     * 创建一个固定的int。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Tag<*>, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        isConcrete = true
        this.javaValue = value
        path.add(MCString(identifier))
    }

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: NBT) : super(b){
        this.path = b.path
    }

    fun isDynamicPath(): Boolean{
        return path.any { !it.isConcrete }
    }

    private fun getPathString(base: String = ""): String {
        var isBase = true
        var pathStr = base
        for ((index, p) in path.withIndex()){
            if(p.isConcrete){
                if(index == 0){
                    pathStr = when(p){
                        is NBT -> {
                            "{${SNBTUtil.toSNBT(p.javaValue)}}"
                        }

                        is MCInt -> {
                            "[${p.javaValue}]"
                        }

                        is MCString -> {
                            "${p.javaValue}"
                        }

                        else -> throw IllegalArgumentException("Invalid path type ${p.type}")
                    }
                }
                else{
                    pathStr += when(p){
                        is NBT -> {
                            "{${SNBTUtil.toSNBT(p.javaValue)}}"
                        }

                        is MCInt -> {
                            "[${p.javaValue}]"
                        }

                        is MCString -> {
                            ".${p.javaValue}"
                        }

                        else -> throw IllegalArgumentException("Invalid path type ${p.type}")
                    }
                }
                isBase = true
            }else{
                if(isBase && pathStr != ""){
                    Function.addCommand("data modify storage mcfpp:arg path_join.base set value \"$pathStr\"")
                    pathStr = ""
                    isBase = false
                }
                when(p){
                    is MCInt -> {
                        if(p.parent != null){
                            Function.addCommands(
                                Commands.selectRun(
                                    p.parent!!,
                                    Command.build("execute store result storage mcfpp:arg append_int.index int 1 run ")
                                        .build(Commands.sbPlayerGet(p)))
                            )
                        }else{
                            Function.addCommand("execute store result storage mcfpp:arg append_int.index int 1 run " +
                                    "scoreboard players get ${p.identifier} ${p.`object`.name}")
                        }
                        Function.addCommand("function mcfpp.dynamic.util:nbt/append_number_index with storage mcfpp:arg append_int")
                    }
                    is MCString -> {
                        if(p.parent != null){
                            Function.addCommands(
                                Commands.selectRun(
                                    p.parent!!,
                                    Command.build("data modify storage mcfpp:arg path_join.pathList append from entity @s data.${p.identifier}")
                                )
                            )
                        }else{
                            Function.addCommand("data modify storage mcfpp:arg path_join.pathList append from storage mcfpp:system ${Project.currNamespace}.stack_frame[${p.stackIndex}].${p.identifier}")
                        }
                    }
                    is NBT -> {
                        val temp = p.getTempVar()
                        Function.addCommand("data modify storage mcfpp:arg path_join.pathList append from storage mcfpp:temp temp.${temp.identifier}")
                    }
                    else -> throw IllegalArgumentException("Invalid path type ${p.type}")
                }
            }
        }
        if(pathStr != ""){
            //动态路径
            Function.addCommand("data modify storage mcfpp:arg path_join.base set value \"$pathStr\"")
            pathStr = ""
        }
        return pathStr
    }

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun assign(b: Var<*>?) {
        hasAssigned = true
        if(b is NBT){
            assignCommand(b)
        }else{
            throw VariableConverseException()
        }
    }

    @InsertCommand
    private fun assignCommand(a: NBT){
        if (parent != null){
            //是成员
            if(a.parent != null){
                //a也是成员
                val b = a.getTempVar() as NBT
                isConcrete = false
                if(this.isDynamicPath() || b.isDynamicPath()){
                    //获取路径至macro
                    val source = this.getPathString()
                    if(source == ""){
                        //动态的，进行解析
                        Function.addCommand("function mcfpp.dynamic.util:nbt/join_path")
                        Function.addCommand("data modify storage mcfpp:arg dynamic.sourcePath set from storage mcfpp:arg path_join.base")
                    }else{
                        //直接写入
                        Function.addCommand("data modity storage mcfpp:arg dynamic.sourcePath set value \"$source\"")
                    }
                    //b的路径直接写入
                    Function.addCommand("data modify storage mcfpp:arg dynamic.targetPath set value \"${b.identifier}\"")
                    //调用宏
                    Function.addCommands(
                        Commands.selectRun(
                            parent!!,
                            Command.build("function mcfpp.dynamic:data/modify/entity.from.storage_sp with storage mcfpp:arg dynamic")
                        )
                    )
                }
                else{
                    Function.addCommands(
                        Commands.selectRun(
                            parent!!,
                            Command.build("data modify entity @s data.${getPathString()} set from storage mcfpp:temp temp.${b.getPathString()}")
                        )
                    )
                }
            }else{
                isConcrete = false
                if(this.isDynamicPath() || a.isDynamicPath()){
                    //获取路径至macro
                    val source = this.getPathString()
                    if(source == ""){
                        //动态的，进行解析
                        Function.addCommand("function mcfpp.dynamic.util:nbt/join_path")
                        Function.addCommand("data modify storage mcfpp:arg dynamic.sourcePath set from storage mcfpp:arg path_join.base")
                    }else{
                        //直接写入
                        Function.addCommand("data modity storage mcfpp:arg dynamic.sourcePath set value \"$source\"")
                    }
                    //获取a的路径
                    val target = a.getPathString("${Project.currNamespace}.stack_frame[${a.stackIndex}]")
                    if(target == "") {
                        //动态的，进行解析
                        Function.addCommand("function mcfpp.dynamic.util:nbt/join_path")
                        Function.addCommand("data modify storage mcfpp:arg dynamic.targetPath set from storage mcfpp:arg path_join.base")
                    }else{
                        //直接写入
                        Function.addCommand("data modify storage mcfpp:arg dynamic.targetPath set value \"$target\"")
                    }
                    //调用宏
                    Function.addCommands(
                        Commands.selectRun(
                            parent!!,
                            Command.build("function mcfpp.dynamic:data/modify/entity.from.storage_sp2 with storage mcfpp:arg dynamic")
                        )
                    )
                }else{
                    Function.addCommands(
                        Commands.selectRun(
                            parent!!,
                            Command.build("data modify entity @s data.${getPathString()} set from storage mcfpp:system ${Project.currNamespace}.stack_frame[${a.stackIndex}].${a.getPathString()}")
                        )
                    )
                }
            }
        }else{
            //是局部变量
            if(a.isConcrete){
                javaValue = a.javaValue
                isConcrete = true
            }else{
                isConcrete = false
                if(a.parent != null){
                    if(this.isDynamicPath() || a.isDynamicPath()){
                        //获取路径至macro
                        val source = this.getPathString("${Project.currNamespace}.stack_frame[$stackIndex]")
                        if(source == ""){
                            //动态的，进行解析
                            Function.addCommand("function mcfpp.dynamic.util:nbt/join_path")
                            Function.addCommand("data modify storage mcfpp:arg dynamic.sourcePath set from storage mcfpp:arg path_join.base")
                        }else{
                            //直接写入
                            Function.addCommand("data modity storage mcfpp:arg dynamic.sourcePath set value \"$source\"")
                        }
                        //获取a的路径
                        val target = a.getPathString()
                        if(target == "") {
                            //动态的，进行解析
                            Function.addCommand("function mcfpp.dynamic.util:nbt/join_path")
                            Function.addCommand("data modify storage mcfpp:arg dynamic.targetPath set from storage mcfpp:arg path_join.base")
                        }else{
                            //直接写入
                            Function.addCommand("data modify storage mcfpp:arg dynamic.targetPath set value \"$target\"")
                        }
                        //调用宏
                        Function.addCommands(
                            Commands.selectRun(
                                a.parent!!,
                                Command.build("function mcfpp.dynamic:data/modify/storage.from.entity_sp3 with storage mcfpp:arg dynamic")
                            )
                        )
                    }else{
                        Function.addCommands(
                            Commands.selectRun(
                                parent!!,
                                Command.build("data modify storage mcfpp:system ${Project.currNamespace}.stack_frame[$stackIndex].${getPathString()} set from entity @s data.${a.getPathString()}")
                            )
                        )
                    }
                }else{
                    if(this.isDynamicPath() || a.isDynamicPath()){
                        //获取路径至macro
                        val source = this.getPathString("${Project.currNamespace}.stack_frame[$stackIndex]")
                        if(source == ""){
                            //动态的，进行解析
                            Function.addCommand("function mcfpp.dynamic.util:nbt/join_path")
                            Function.addCommand("data modify storage mcfpp:arg dynamic.sourcePath set from storage mcfpp:arg path_join.base")
                        }else{
                            //直接写入
                            Function.addCommand("data modity storage mcfpp:arg dynamic.sourcePath set value \"$source\"")
                        }
                        //获取a的路径
                        val target = a.getPathString("${Project.currNamespace}.stack_frame[${a.stackIndex}]")
                        if(target == "") {
                            //动态的，进行解析
                            Function.addCommand("function mcfpp.dynamic.util:nbt/join_path")
                            Function.addCommand("data modify storage mcfpp:arg dynamic.targetPath set from storage mcfpp:arg path_join.base")
                        }else{
                            //直接写入
                            Function.addCommand("data modify storage mcfpp:arg dynamic.targetPath set value \"$target\"")
                        }
                        //调用宏
                        Function.addCommand(
                            Command.build("function mcfpp.dynamic:data/modify/storage.from.storage_sp4 with storage mcfpp:arg dynamic")
                        )
                    }
                    Function.addCommand(
                        Command.build("data modify storage mcfpp:system ${Project.currNamespace}.stack_frame[$stackIndex].${getPathString()} set from storage mcfpp:system ${Project.currNamespace}.stack_frame[${a.stackIndex}].${a.getPathString()}")
                    )
                }
            }
        }
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun cast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPNBTType -> this
            MCFPPBaseType.Any -> MCAny(this)
            else -> throw VariableConverseException()
        }
    }

    override fun clone(): NBT {
        return NBT(this)
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    override fun getTempVar(): Var<*> {
        if(isConcrete){
            return NBT(this.javaValue!!)
        }
        val temp = NBT()
        if(isDynamicPath()){
            if(parent != null){
                this.getPathString()
                //动态的，进行解析
                Function.addCommand("function mcfpp.dynamic.util:nbt/join_path")
                Function.addCommand("data modify storage mcfpp:arg dynamic.targetPath set from storage mcfpp:arg path_join.base")
                Function.addCommand("data modify storage mcfpp:arg dynamic.sourcePath set value ${temp.identifier}")
                //调用宏
                Function.addCommands(
                    Commands.selectRun(
                        parent!!,
                        Command.build("function mcfpp.dynamic:data/modify/storage.from.entity_sp5 with storage mcfpp:arg dynamic")
                    )
                )
            }else{
                this.getPathString("${Project.currNamespace}.stack_frame[$stackIndex]")
                Function.addCommand("function mcfpp.dynamic.util:nbt/join_path")
                Function.addCommand("data modify storage mcfpp:arg dynamic.targetPath set from storage mcfpp:arg path_join.base")
                Function.addCommand("data modify storage mcfpp:arg dynamic.sourcePath set value ${temp.identifier}")
                //调用宏
                Function.addCommand("function mcfpp.dynamic:data/modify/storage.from.entity_sp6 with storage mcfpp:arg dynamic")
            }
        }else if(parent != null){
            Function.addCommands(
                Commands.selectRun(
                    parent!!,
                    Command.build("data modify storage mcfpp:temp temp.${temp.identifier} set from entity @s data.${getPathString()}")
                )
            )
        }else{
            Function.addCommand("data modify storage mcfpp:temp temp.${temp.identifier} set from storage mcfpp:system ${Project.currNamespace}.stack_frame[${stackIndex}].${getPathString()}")
        }
        return temp
    }

    override fun storeToStack() {
        //什么都不用做哦
        return
    }

    override fun getFromStack() {
        //什么都不用做哦
        return
    }

    override fun toDynamic() {
        val parent = parent
        if(!isConcrete) return
        isConcrete = false
        if (parent != null) {
            val cmd = when(parent){
                is ClassPointer -> {
                    Commands.selectRun(parent)
                }
                is ClassType -> {
                    arrayOf(Command.build("execute as ${(parent.dataType as Class).uuid} run "))
                }
                else -> TODO()
            }
            if(cmd.size == 2){
                Function.addCommand(cmd[0])
            }
            Function.addCommand(cmd.last().build("data modify entity @s data.${identifier} set value ${SNBTUtil.toSNBT(
                javaValue
            )}"))
        } else {
            val cmd = Command.build("data modify storage mcfpp:system ${Project.currNamespace}.stack_frame[$stackIndex].$identifier set value ${SNBTUtil.toSNBT(
                javaValue
            )}")
            Function.addCommand(cmd)
        }
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

    /**
     * 根据方法标识符和方法的参数列表获取一个方法。如果没有这个方法，则返回null
     *
     * @param key 成员方法的标识符
     * @param params 成员方法的参数
     * @return
     */
    override fun getMemberFunction(
        key: String,
        params: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }

    override fun getByIndex(index: Var<*>): NBT {
        return when(index){
            is MCInt -> getByIntIndex(index)
            is MCString -> getByStringIndex(index)
            is NBT -> getByNBTIndex(index)
            else -> throw IllegalArgumentException("Invalid index type ${index.type}")
        }
    }

    fun getByNBTIndex(index: NBT): NBT{
        if(nbtType != NBTType.LIST && nbtType != NBTType.ANY){
            LogProcessor.error("Invalid nbt type")
        }
        if(index.nbtType != NBTType.COMPOUND && index.nbtType != NBTType.ANY){
            LogProcessor.error("Invalid nbt type")
        }
        val re = NBT(this)
        re.path.add(index)
        return re
    }

    fun getByStringIndex(index: MCString): NBT {
        if(nbtType != NBTType.COMPOUND && nbtType != NBTType.ANY){
            LogProcessor.error("Invalid nbt type")
        }
        val re = NBT(this)
        re.path.add(index)
        return re
    }

    fun getByIntIndex(index: MCInt): NBT {
        if(nbtType != NBTType.LIST && nbtType != NBTType.ANY){
            LogProcessor.error("Invalid nbt type")
        }
        val re = NBT(this)
        re.path.add(index)
        return re
    }

    override fun getVarValue(): Any? {
        return javaValue
    }

    companion object {
        enum class NBTType {
            COMPOUND, LIST, VALUE, ANY
        }
    }
}