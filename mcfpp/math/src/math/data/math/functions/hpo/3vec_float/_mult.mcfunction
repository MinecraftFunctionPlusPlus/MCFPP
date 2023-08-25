#math:hpo/3vec_float/_mult

#高低位
scoreboard players operation sstemp0 int = 3vec_float_x_int0 int
scoreboard players operation sstemp0 int *= @s float_int1
scoreboard players operation sstemp1 int = 3vec_float_x_int1 int
scoreboard players operation sstemp1 int *= @s float_int1
scoreboard players operation sstemp1 int /= 10000 int
scoreboard players operation 3vec_float_x_int1 int *= @s float_int0
scoreboard players operation 3vec_float_x_int1 int += sstemp0 int
scoreboard players operation 3vec_float_x_int1 int += sstemp1 int
scoreboard players operation 3vec_float_x_int0 int *= @s float_int0
scoreboard players operation 3vec_float_x_int0 int *= 10 int
scoreboard players operation 3vec_float_x_int1 int /= 1000 int
scoreboard players operation 3vec_float_x_int0 int += 3vec_float_x_int1 int
#符号位
scoreboard players operation 3vec_float_x_sign int *= @s float_sign
#指数位
scoreboard players operation 3vec_float_x_exp int += @s float_exp
scoreboard players remove 3vec_float_x_exp int 1
#小数位对齐
execute if score 3vec_float_x_int0 int matches 100000000.. run function math:hpo/3vec_float/mult_alignx
scoreboard players operation 3vec_float_x_int1 int = 3vec_float_x_int0 int
scoreboard players operation 3vec_float_x_int0 int /= 10000 int
scoreboard players operation 3vec_float_x_int1 int %= 10000 int

#高低位
scoreboard players operation sstemp0 int = 3vec_float_y_int0 int
scoreboard players operation sstemp0 int *= @s float_int1
scoreboard players operation sstemp1 int = 3vec_float_y_int1 int
scoreboard players operation sstemp1 int *= @s float_int1
scoreboard players operation sstemp1 int /= 10000 int
scoreboard players operation 3vec_float_y_int1 int *= @s float_int0
scoreboard players operation 3vec_float_y_int1 int += sstemp0 int
scoreboard players operation 3vec_float_y_int1 int += sstemp1 int
scoreboard players operation 3vec_float_y_int0 int *= @s float_int0
scoreboard players operation 3vec_float_y_int0 int *= 10 int
scoreboard players operation 3vec_float_y_int1 int /= 1000 int
scoreboard players operation 3vec_float_y_int0 int += 3vec_float_y_int1 int
#符号位
scoreboard players operation 3vec_float_y_sign int *= @s float_sign
#指数位
scoreboard players operation 3vec_float_y_exp int += @s float_exp
scoreboard players remove 3vec_float_y_exp int 1
#小数位对齐
execute if score 3vec_float_y_int0 int matches 100000000.. run function math:hpo/3vec_float/mult_aligny
scoreboard players operation 3vec_float_y_int1 int = 3vec_float_y_int0 int
scoreboard players operation 3vec_float_y_int0 int /= 10000 int
scoreboard players operation 3vec_float_y_int1 int %= 10000 int

#高低位
scoreboard players operation sstemp0 int = 3vec_float_z_int0 int
scoreboard players operation sstemp0 int *= @s float_int1
scoreboard players operation sstemp1 int = 3vec_float_z_int1 int
scoreboard players operation sstemp1 int *= @s float_int1
scoreboard players operation sstemp1 int /= 10000 int
scoreboard players operation 3vec_float_z_int1 int *= @s float_int0
scoreboard players operation 3vec_float_z_int1 int += sstemp0 int
scoreboard players operation 3vec_float_z_int1 int += sstemp1 int
scoreboard players operation 3vec_float_z_int0 int *= @s float_int0
scoreboard players operation 3vec_float_z_int0 int *= 10 int
scoreboard players operation 3vec_float_z_int1 int /= 1000 int
scoreboard players operation 3vec_float_z_int0 int += 3vec_float_z_int1 int
#符号位
scoreboard players operation 3vec_float_z_sign int *= @s float_sign
#指数位
scoreboard players operation 3vec_float_z_exp int += @s float_exp
scoreboard players remove 3vec_float_z_exp int 1
#小数位对齐
execute if score 3vec_float_z_int0 int matches 100000000.. run function math:hpo/3vec_float/mult_alignz
scoreboard players operation 3vec_float_z_int1 int = 3vec_float_z_int0 int
scoreboard players operation 3vec_float_z_int0 int /= 10000 int
scoreboard players operation 3vec_float_z_int1 int %= 10000 int