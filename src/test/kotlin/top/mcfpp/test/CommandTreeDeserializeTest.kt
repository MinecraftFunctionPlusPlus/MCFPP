package top.mcfpp.test

import com.alibaba.fastjson2.JSON
import top.mcfpp.Project
import top.mcfpp.command.tree.CommandTreeNode
import top.mcfpp.command.tree.DownloadHelper
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.test.Test
class CommandTreeDeserializeTest {
    @Test
    fun testDeserialize(){
        val code = """
            {
                "type": "root",
                "children": {
                    "time": {
                      "type": "literal",
                      "children": {
                        "add": {
                          "type": "literal",
                          "children": {
                            "time": {
                              "type": "argument",
                              "executable": true,
                              "parser": "minecraft:time",
                              "properties": {
                                "min": 0
                              }
                            }
                          }
                        },
                        "query": {
                          "type": "literal",
                          "children": {
                            "day": {
                              "type": "literal",
                              "executable": true
                            },
                            "daytime": {
                              "type": "literal",
                              "executable": true
                            },
                            "gametime": {
                              "type": "literal",
                              "executable": true
                            }
                          }
                        },
                        "set": {
                          "type": "literal",
                          "children": {
                            "day": {
                              "type": "literal",
                              "executable": true
                            },
                            "midnight": {
                              "type": "literal",
                              "executable": true
                            },
                            "night": {
                              "type": "literal",
                              "executable": true
                            },
                            "noon": {
                              "type": "literal",
                              "executable": true
                            },
                            "time": {
                              "type": "argument",
                              "executable": true,
                              "parser": "minecraft:time",
                              "properties": {
                                "min": 0
                              }
                            }
                          }
                        }
                      }
                    }
                }
            }
        """.trimIndent()
        val commandTree = JSON.parseObject(code,CommandTreeNode::class.java)
        println(commandTree.children?.get("time")?.children?.get("set")?.children?.get("time")?.properties )

    }

    @Test
    fun testDownload(){
        Project.root = Path.of("./")
        val commandTree =DownloadHelper.getMcmetaCommands("24w03b",false,"fastly")
        println(commandTree.children?.get("teleport")?.children?.get("destination")?.properties )

    }

}