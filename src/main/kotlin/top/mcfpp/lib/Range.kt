package top.mcfpp.lib

@Deprecated("Use RangeVar instead")
interface Range<T>{
    fun inRange(value: T): Boolean
    fun clone(): Range<T>
}

@Deprecated("Use RangeVar instead")
abstract class OneValueRange<T>(var value : T) : Range<T> {
    override fun inRange(value: T): Boolean {
        return value == this.value
    }
}

@Deprecated("Use RangeVar instead")
abstract class TwoValueRange<T>(var start: T?, var end: T?) : Range<T> {

    override fun inRange(value: T): Boolean {
        if(start == null && end == null) throw NullPointerException("Range should have at least one side")
        val re = if(start != null) leftValueCompare(value) else true
        return re && rightValueCompare(value)
    }

    abstract fun leftValueCompare(value: T) : Boolean

    abstract fun rightValueCompare(value: T) : Boolean

}

@Deprecated("Use RangeVar instead")
class OneIntConcreteRange(value: Int): OneValueRange<Int>(value){
    override fun clone(): Range<Int> {
        return OneIntConcreteRange(value)
    }
}

@Deprecated("Use RangeVar instead")
class TwoIntConcreteRange(start: Int?, end: Int?) : TwoValueRange<Int>(start, end) {
    override fun leftValueCompare(value: Int): Boolean {
        return value >= start!!
    }

    override fun rightValueCompare(value: Int): Boolean {
        return value <= end!!
    }

    override fun clone(): Range<Int> {
        return TwoIntConcreteRange(start, end)
    }
}

@Deprecated("Use RangeVar instead")
class OneFloatConcreteRange(value: Float) : OneValueRange<Float>(value){
    override fun clone(): Range<Float> {
        return OneFloatConcreteRange(value)
    }
}

@Deprecated("Use RangeVar instead")
class TwoFloatConcreteRange(start: Float?, end: Float?) : TwoValueRange<Float>(start, end) {
    override fun leftValueCompare(value: Float): Boolean {
        return value >= start!!
    }

    override fun rightValueCompare(value: Float): Boolean {
        return value <= end!!
    }

    override fun clone(): Range<Float> {
        return TwoFloatConcreteRange(start, end)
    }
}