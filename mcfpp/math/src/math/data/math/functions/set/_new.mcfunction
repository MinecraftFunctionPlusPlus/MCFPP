#math:set/_new
# 输入数据模板storage math:io input
# 返回一个集合对象

tag @e remove result
summon marker 0 11 0 {Tags:["result","set"],data:{set:[],set_tags:[]}}
execute as @e[tag=result,limit=1] run function math:set/set