package top.mcfpp.compiletime

sealed class MCControl {
    object Break : MCControl();
    object Continue : MCControl();
}