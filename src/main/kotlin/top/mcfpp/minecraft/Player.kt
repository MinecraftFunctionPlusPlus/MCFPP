package top.mcfpp.minecraft

import top.mcfpp.model.CompoundData
import top.mcfpp.model.field.GlobalField

object Player : CompoundData("Player") {
    init {
        GlobalField.stdNamespaces["minecraft"]!!.field.addObject("Player", this)
    }

}