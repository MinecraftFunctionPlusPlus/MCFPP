package top.mcfpp.backend

import top.mcfpp.ProjectConfig
import top.mcfpp.command.CommentLevel
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.lib.SbObject
import top.mcfpp.model.function.Function

/** 根据实际链接到的命令生成运行时依赖的初始化代码，不持有工程或库会话。 */
internal object RuntimeInitialization {
    /**
     * 在调用方的 load 函数上下文中执行。依赖必须先于对象构造器初始化，
     * 因此调用方在这些命令之后追加已编译的初始化函数体。
     */
    fun emit(config: ProjectConfig, scoreboards: Collection<SbObject>, runtimeCommands: String) {
        // 目标名按完整 token 匹配，避免把名称包含关系误判为运行时依赖。
        for (scoreboard in scoreboards.filter {
            Regex("(?<![a-zA-Z0-9_.-])" + Regex.escape(it.name) + "(?![a-zA-Z0-9_.-])")
                .containsMatchIn(runtimeCommands)
        }) {
            Function.addCommand("scoreboard objectives add ${scoreboard.name} ${scoreboard.criterion}")
        }

        if (Regex("(?<![a-z0-9_.-])math(?:\\.[a-z0-9_-]+)*:[a-z0-9_./-]+").containsMatchIn(runtimeCommands)) {
            Function.addComment("math:_init", CommentLevel.INFO)
            Function.addCommand("scoreboard objectives add mcfpp_init dummy")
            // math3.1 adds numeric constants. Worlds initialized by the previous library
            // already have math=1, so track the numeric revision independently.
            Function.addCommand("execute unless score math_version mcfpp_init matches 315 run function math:_init")
            Function.addCommand("scoreboard players set math_version mcfpp_init 315")
        }

        if (runtimeCommands.contains("mcfpp_ptr_marker") || runtimeCommands.contains(config.tempItemEntityUUID.uuidSNBT)) {
            Function.addCommand("summon item 0 0 0 {" +
                "Tags:[\"mcfpp_ptr_marker\"]," +
                "UUID:${config.tempItemEntityUUID.uuidSNBT}, " +
                "Age:-32768, " +
                "NoGravity: true, " +
                "Item:{id:\"stone\"}, " +
                "Invulnerable: true" +
                "}"
            )
        }

        Function.addComment("class init", CommentLevel.INFO)

        // 原生浮点后端不需要旧数学库用于保存浮点数的 marker 实体。
        if (!FloatProviders.enabled && (runtimeCommands.contains("mcfpp_float_marker") ||
                runtimeCommands.contains(MCFloat.tempFloatEntityUUIDNBT) || runtimeCommands.contains("math.float:"))) {
            Function.addCommand("summon marker 0 0 0 {" +
                "Tags:[\"mcfpp_float_marker\"]," +
                "UUID:${MCFloat.tempFloatEntityUUIDNBT}}"
            )
        }
    }
}
