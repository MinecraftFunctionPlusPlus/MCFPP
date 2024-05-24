package top.mcfpp.command

import top.mcfpp.Project

class CommandList : ArrayList<Command>() {
    fun analyzeAll(): ArrayList<String> {
        val re = ArrayList<String>()
        for (c in this) {
            if (c is Comment && c.type < Project.config.commentLevel) continue
            re.add(c.analyze())
        }
        return re
    }

    fun replaceAll(vararg pointIDtoTarget: Pair<String, String>) {
        for (c in this) {
            c.replace(*pointIDtoTarget)
        }
    }

    fun replaceThenAnalyze(vararg pointIDtoTarget: Pair<String, String>) {
        for (c in this) {
            if (c.replace(*pointIDtoTarget) != 0) {
                c.analyze()
            }
        }
    }

    fun add(element: String): Boolean {
        return super.add(Command.build(element))
    }

    fun add(index: Int, element: String) {
        return super.add(index, Command.build(element))
    }

}