package top.alumopper.mcfpp.lib

import top.alumopper.mcfpp.lang.ClassPointer
import top.alumopper.mcfpp.lang.Var

class NativeConstructor(cls: Class?) : Constructor(cls), Native {
    @Override
    override operator fun invoke(args: ArrayList<Var>, lineNo: Int, cls: ClassPointer) {
        TODO()
    }
}