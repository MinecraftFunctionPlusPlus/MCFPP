package top.mcfpp.compiletime

import top.mcfpp.lang.Var

sealed class MCControl {
    object Break:MCControl();
    object Continue:MCControl();
}