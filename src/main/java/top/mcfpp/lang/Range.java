package top.mcfpp.lang;
public class Range<T extends MCNumber<?>> {

    /**
     * 闭区间左端
     */
    public T start;

    /**
     * 闭区间右端
     */
    public T end;


    /**
     * 构造一个区间
     * @param start 区间左端。若为null则没有左端。
     * @param end 区间右端。若为null则没有右端。
     */
    public Range(T start, T end){
        this.start = start;
        this.end = end;
    }



    @Override
    public String toString() {
        return  (start != null ? start : "") +  ".." + (end != null ? end : "");
    }
}
