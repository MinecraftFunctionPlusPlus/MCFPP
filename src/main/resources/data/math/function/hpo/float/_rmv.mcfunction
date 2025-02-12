#math:hpo/float/_rmv
#判断绝对值大小
scoreboard players operation sstemp_sign int = @s float_sign
scoreboard players operation sstemp_sign int *= float_sign int
scoreboard players set sstempr int 1
scoreboard players operation float_int0 int *= 10000 int
scoreboard players operation float_int0 int += float_int1 int
scoreboard players operation sstemp1 int = @s float_int0
scoreboard players operation sstemp1 int *= 10000 int
scoreboard players operation sstemp1 int += @s float_int1
execute if score float_exp int > @s float_exp run scoreboard players set sstempr int 0
execute if score float_exp int = @s float_exp if score float_int0 int >= sstemp1 int run scoreboard players set sstempr int 0

#指数
scoreboard players operation sstempe int = float_exp int
scoreboard players operation sstempe int -= @s float_exp
#交换使得大数在前
execute if score sstempr int matches 1 run function math:hpo/float/rmv_swap
#符号
scoreboard players operation sstemp1 int *= sstemp_sign int
#对齐
execute if score sstempe int matches 1..2 run function math:hpo/float/add_search/1_2
execute if score sstempe int matches 3..5 run function math:hpo/float/add_search/3_5
execute if score sstempe int matches 6.. run function math:hpo/float/add_search/6_8
scoreboard players operation float_int0 int -= sstemp1 int

#对齐小数点
execute if score float_int0 int matches 100000.. run function math:hpo/float/add_search/align_s2
execute if score float_int0 int matches 100..99999 run function math:hpo/float/add_search/align_s1
execute if score float_int0 int matches 0..99 run function math:hpo/float/add_search/align_s0

scoreboard players operation float_int1 int = float_int0 int
scoreboard players operation float_int0 int /= 10000 int
scoreboard players operation float_int1 int %= 10000 int