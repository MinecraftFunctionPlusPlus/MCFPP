#math:hpo/float/_scoreto

#符号
execute store result score float_int0 int run scoreboard players operation float_sign int = inp int
execute if score float_int0 int matches ..-1 run scoreboard players operation float_int0 int *= -1 int
scoreboard players operation float_sign int /= float_int0 int

#指数
scoreboard players set float_exp int 4

#对齐小数点
execute if score float_int0 int matches 100000.. run function math:hpo/float/add_search/align_s2
execute if score float_int0 int matches 100..99999 run function math:hpo/float/add_search/align_s1
execute if score float_int0 int matches 0..99 run function math:hpo/float/add_search/align_s0

#高位低位
scoreboard players operation float_int1 int = float_int0 int
scoreboard players operation float_int0 int /= 10000 int
scoreboard players operation float_int1 int %= 10000 int