package top.mcfpp.testing

import top.mcfpp.model.annotation.FunctionAnnotation
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.util.LogProcessor

/** These annotations describe MCFPP functions, not JVM test methods. */
abstract class TestAnnotation(name: String) : FunctionAnnotation(name, "mcfpp.test") {
    override fun forFunction(function: Function) {
        if (function.owner != null || function.normalParams.isNotEmpty() ||
            function.javaClass != Function::class.java || function.isAbstract ||
            function.returnType != MCFPPPrivateType.Void) {
            LogProcessor.error("@$identifier requires a top-level, parameterless, non-generic void function")
        }
    }
}

class Test : TestAnnotation("Test")
class BeforeEach : TestAnnotation("BeforeEach")
class AfterEach : TestAnnotation("AfterEach")
class Disabled() : TestAnnotation("Disabled") {
    var reason: String = "Disabled"
        private set
    constructor(reason: String) : this() { this.reason = reason }
}
