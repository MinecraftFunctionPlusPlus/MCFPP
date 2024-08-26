package top.mcfpp.`var`.lang

interface ScoreHolder {

    /**
     * @param score 更改之后的成员变量
     *
     * 当成员变量发生变化时，会调用此方法
     */
    fun onScoreChange(score: MCInt)

}