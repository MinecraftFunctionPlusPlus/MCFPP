package com.example

import top.mcfpp.MCFPP
import net.fabricmc.api.ModInitializer

fun main(){

}

object DevTest: ModInitializer {
    override fun onInitialize() {
        println("MCFPP Testing Environment")
        println(MCFPP.version)
    }
}