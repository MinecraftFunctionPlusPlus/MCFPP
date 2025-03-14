package top.mcfpp.core.lang.entity

import top.mcfpp.command.Command

interface AbstractEntity {

    fun toCommandPart(): Command

}