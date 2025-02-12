#math:hpo/3vec_float/_add

function math:hpo/3vec_float/_ssprintx
#判断绝对值大小
scoreboard players operation sstemp_sign int = @s 3vec_float_x_sign
scoreboard players operation sstemp_sign int *= 3vec_float_x_sign int
scoreboard players set sstempr int 1
scoreboard players operation 3vec_float_x_int0 int *= 10000 int
scoreboard players operation 3vec_float_x_int0 int += 3vec_float_x_int1 int
scoreboard players operation sstemp1 int = @s 3vec_float_x_int0
scoreboard players operation sstemp1 int *= 10000 int
scoreboard players operation sstemp1 int += @s 3vec_float_x_int1
execute if score 3vec_float_x_exp int > @s 3vec_float_x_exp run scoreboard players set sstempr int 0
execute if score 3vec_float_x_exp int = @s 3vec_float_x_exp if score 3vec_float_x_int0 int >= sstemp1 int run scoreboard players set sstempr int 0
#指数
scoreboard players operation sstempe int = 3vec_float_x_exp int
scoreboard players operation sstempe int -= @s 3vec_float_x_exp
#交换使得大数在前
execute if score sstempr int matches 1 run function math:hpo/3vec_float/add_swapx
#符号
scoreboard players operation sstemp1 int *= sstemp_sign int
#对齐
execute if score sstempe int matches 1..2 run function math:hpo/float/add_search/1_2
execute if score sstempe int matches 3..5 run function math:hpo/float/add_search/3_5
execute if score sstempe int matches 6.. run function math:hpo/float/add_search/6_8
scoreboard players operation 3vec_float_x_int0 int += sstemp1 int
#对齐小数点
execute if score 3vec_float_x_int0 int matches 100000.. run function math:hpo/3vec_float/addx_search/align_s2
execute if score 3vec_float_x_int0 int matches 100..99999 run function math:hpo/3vec_float/addx_search/align_s1
execute if score 3vec_float_x_int0 int matches 0..99 run function math:hpo/3vec_float/addx_search/align_s0
scoreboard players operation 3vec_float_x_int1 int = 3vec_float_x_int0 int
scoreboard players operation 3vec_float_x_int0 int /= 10000 int
scoreboard players operation 3vec_float_x_int1 int %= 10000 int
function math:hpo/3vec_float/_ssprintx

#判断绝对值大小
scoreboard players operation sstemp_sign int = @s 3vec_float_y_sign
scoreboard players operation sstemp_sign int *= 3vec_float_y_sign int
scoreboard players set sstempr int 1
scoreboard players operation 3vec_float_y_int0 int *= 10000 int
scoreboard players operation 3vec_float_y_int0 int += 3vec_float_y_int1 int
scoreboard players operation sstemp1 int = @s 3vec_float_y_int0
scoreboard players operation sstemp1 int *= 10000 int
scoreboard players operation sstemp1 int += @s 3vec_float_y_int1
execute if score 3vec_float_y_exp int > @s 3vec_float_y_exp run scoreboard players set sstempr int 0
execute if score 3vec_float_y_exp int = @s 3vec_float_y_exp if score 3vec_float_y_int0 int >= sstemp1 int run scoreboard players set sstempr int 0
#指数
scoreboard players operation sstempe int = 3vec_float_y_exp int
scoreboard players operation sstempe int -= @s 3vec_float_y_exp
#交换使得大数在前
execute if score sstempr int matches 1 run function math:hpo/3vec_float/add_swapy
#符号
scoreboard players operation sstemp1 int *= sstemp_sign int
#对齐
execute if score sstempe int matches 1..2 run function math:hpo/float/add_search/1_2
execute if score sstempe int matches 3..5 run function math:hpo/float/add_search/3_5
execute if score sstempe int matches 6.. run function math:hpo/float/add_search/6_8
scoreboard players operation 3vec_float_y_int0 int += sstemp1 int
#对齐小数点
execute if score 3vec_float_y_int0 int matches 100000.. run function math:hpo/3vec_float/addy_search/align_s2
execute if score 3vec_float_y_int0 int matches 100..99999 run function math:hpo/3vec_float/addy_search/align_s1
execute if score 3vec_float_y_int0 int matches 0..99 run function math:hpo/3vec_float/addy_search/align_s0
scoreboard players operation 3vec_float_y_int1 int = 3vec_float_y_int0 int
scoreboard players operation 3vec_float_y_int0 int /= 10000 int
scoreboard players operation 3vec_float_y_int1 int %= 10000 int

#判断绝对值大小
scoreboard players operation sstemp_sign int = @s 3vec_float_z_sign
scoreboard players operation sstemp_sign int *= 3vec_float_z_sign int
scoreboard players set sstempr int 1
scoreboard players operation 3vec_float_z_int0 int *= 10000 int
scoreboard players operation 3vec_float_z_int0 int += 3vec_float_z_int1 int
scoreboard players operation sstemp1 int = @s 3vec_float_z_int0
scoreboard players operation sstemp1 int *= 10000 int
scoreboard players operation sstemp1 int += @s 3vec_float_z_int1
execute if score 3vec_float_z_exp int > @s 3vec_float_z_exp run scoreboard players set sstempr int 0
execute if score 3vec_float_z_exp int = @s 3vec_float_z_exp if score 3vec_float_z_int0 int >= sstemp1 int run scoreboard players set sstempr int 0
#指数
scoreboard players operation sstempe int = 3vec_float_z_exp int
scoreboard players operation sstempe int -= @s 3vec_float_z_exp
#交换使得大数在前
execute if score sstempr int matches 1 run function math:hpo/3vec_float/add_swapz
#符号
scoreboard players operation sstemp1 int *= sstemp_sign int
#对齐
execute if score sstempe int matches 1..2 run function math:hpo/float/add_search/1_2
execute if score sstempe int matches 3..5 run function math:hpo/float/add_search/3_5
execute if score sstempe int matches 6.. run function math:hpo/float/add_search/6_8
scoreboard players operation 3vec_float_z_int0 int += sstemp1 int
#对齐小数点
execute if score 3vec_float_z_int0 int matches 100000.. run function math:hpo/3vec_float/addz_search/align_s2
execute if score 3vec_float_z_int0 int matches 100..99999 run function math:hpo/3vec_float/addz_search/align_s1
execute if score 3vec_float_z_int0 int matches 0..99 run function math:hpo/3vec_float/addz_search/align_s0
scoreboard players operation 3vec_float_z_int1 int = 3vec_float_z_int0 int
scoreboard players operation 3vec_float_z_int0 int /= 10000 int
scoreboard players operation 3vec_float_z_int1 int %= 10000 int