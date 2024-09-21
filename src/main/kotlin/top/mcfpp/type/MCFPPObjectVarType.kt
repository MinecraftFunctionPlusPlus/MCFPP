package top.mcfpp.type

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Class
import top.mcfpp.model.FieldContainer
import top.mcfpp.util.LogProcessor

object MCFPPStdPrivateType{

    object MCFPPObjectVarType: MCFPPPrivateType() {
        override val typeName: String
            get() = "ObjectVar"
    }

    object MCFPPCoordinateDimension: MCFPPPrivateType(){
        override val typeName: String
            get() = "CoordinateDimension"
    }
}
