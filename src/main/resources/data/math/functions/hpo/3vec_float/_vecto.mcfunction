#math:hpo/3vec_float/_vecto

#符号
execute store result score 3vec_float_x_int0 int run scoreboard players operation 3vec_float_x_sign int = 3vec_x int
execute if score 3vec_float_x_int0 int matches ..-1 run scoreboard players operation 3vec_float_x_int0 int *= -1 int
scoreboard players operation 3vec_float_x_sign int /= 3vec_float_x_int0 int
#指数
scoreboard players set 3vec_float_x_exp int 4
#对齐小数点
execute if score 3vec_float_x_int0 int matches 100000.. run function math:hpo/3vec_float/addx_search/align_s2
execute if score 3vec_float_x_int0 int matches 100..99999 run function math:hpo/3vec_float/addx_search/align_s1
execute if score 3vec_float_x_int0 int matches 0..99 run function math:hpo/3vec_float/addx_search/align_s0
#高位低位
scoreboard players operation 3vec_float_x_int1 int = 3vec_float_x_int0 int
scoreboard players operation 3vec_float_x_int0 int /= 10000 int
scoreboard players operation 3vec_float_x_int1 int %= 10000 int

#符号
execute store result score 3vec_float_y_int0 int run scoreboard players operation 3vec_float_y_sign int = 3vec_y int
execute if score 3vec_float_y_int0 int matches ..-1 run scoreboard players operation 3vec_float_y_int0 int *= -1 int
scoreboard players operation 3vec_float_y_sign int /= 3vec_float_y_int0 int
#指数
scoreboard players set 3vec_float_y_exp int 4
#对齐小数点
execute if score 3vec_float_y_int0 int matches 100000.. run function math:hpo/3vec_float/addy_search/align_s2
execute if score 3vec_float_y_int0 int matches 100..99999 run function math:hpo/3vec_float/addy_search/align_s1
execute if score 3vec_float_y_int0 int matches 0..99 run function math:hpo/3vec_float/addy_search/align_s0
#高位低位
scoreboard players operation 3vec_float_y_int1 int = 3vec_float_y_int0 int
scoreboard players operation 3vec_float_y_int0 int /= 10000 int
scoreboard players operation 3vec_float_y_int1 int %= 10000 int

#符号
execute store result score 3vec_float_z_int0 int run scoreboard players operation 3vec_float_z_sign int = 3vec_z int
execute if score 3vec_float_z_int0 int matches ..-1 run scoreboard players operation 3vec_float_z_int0 int *= -1 int
scoreboard players operation 3vec_float_z_sign int /= 3vec_float_z_int0 int
#指数
scoreboard players set 3vec_float_z_exp int 4
#对齐小数点
execute if score 3vec_float_z_int0 int matches 100000.. run function math:hpo/3vec_float/addz_search/align_s2
execute if score 3vec_float_z_int0 int matches 100..99999 run function math:hpo/3vec_float/addz_search/align_s1
execute if score 3vec_float_z_int0 int matches 0..99 run function math:hpo/3vec_float/addz_search/align_s0
#高位低位
scoreboard players operation 3vec_float_z_int1 int = 3vec_float_z_int0 int
scoreboard players operation 3vec_float_z_int0 int /= 10000 int
scoreboard players operation 3vec_float_z_int1 int %= 10000 int