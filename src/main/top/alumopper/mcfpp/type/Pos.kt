package top.alumopper.mcfpp.type

class Pos @JvmOverloads constructor(
    x: Double = 0.0,
    y: Double = 0.0,
    z: Double = 0.0,
    private val related: Boolean = false
) : Vector3<Double?>(x, y, z)