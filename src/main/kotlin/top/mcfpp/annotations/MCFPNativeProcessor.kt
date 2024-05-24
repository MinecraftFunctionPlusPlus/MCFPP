package top.mcfpp.annotations

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

class MCFPNativeProcessor : AbstractProcessor() {

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(MCFPPNative::class.java.name)
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        roundEnv?.getElementsAnnotatedWith(MCFPPNative::class.java)?.forEach { element ->
            if (element.kind == ElementKind.METHOD) {
                val methodElement = element as ExecutableElement
                val parameters = methodElement.parameters
                if (parameters.size != 2 || parameters[0].asType().toString() != "Var[]" || parameters[1].asType()
                        .toString() != "CanSelectMember"
                ) {
                    processingEnv.messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Method ${methodElement.simpleName} annotated with @MCFPNative must have parameters Var[] vars, CanSelectMember caller"
                    )
                }
            }
        }
        return true
    }
}