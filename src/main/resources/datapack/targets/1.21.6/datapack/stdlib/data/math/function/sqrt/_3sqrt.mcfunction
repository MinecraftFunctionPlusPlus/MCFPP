#math:sqrt/_3sqrt
# 3位小数精度开根号
# 输入<inp,int>
# 输出<res,int>

execute if score inp int matches ..10000 run function math:sqrt/range3
execute if score inp int matches 10001.. run function math:sqrt/range4
