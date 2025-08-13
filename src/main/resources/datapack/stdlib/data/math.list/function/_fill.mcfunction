#math.list:_fill
# 输入个数<inp,int>
# 用storage math:io input填充list

scoreboard players operation sloop int = inp int
execute if score sloop int matches 1.. run function math.list:fill_sloop