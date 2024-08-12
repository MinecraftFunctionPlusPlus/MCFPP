#math:hpo/3vec_float/_div

#由万进制节计算除数和被除数
scoreboard players operation sstempd int = @s float_int0
scoreboard players operation sstempd int *= 10000 int
scoreboard players operation sstempd int += @s float_int1
scoreboard players set float_int0 int 100000000

#计算结果前后4位精度
scoreboard players operation sstemp0 int = sstempd int
scoreboard players operation sstempd int /= 25 int
scoreboard players operation sstemp0 int %= 25 int
scoreboard players operation sstemp1 int = float_int0 int
scoreboard players operation float_int0 int /= sstempd int
scoreboard players operation float_int0 int *= 40 int
scoreboard players operation sstemp1 int %= sstempd int
scoreboard players operation sstemp1 int *= 40 int
scoreboard players operation float_int1 int = sstemp1 int
scoreboard players operation sstemp1 int /= sstempd int
scoreboard players operation float_int0 int += sstemp1 int
scoreboard players operation float_int1 int %= sstempd int
scoreboard players operation float_int1 int *= 25 int
scoreboard players operation sstemp2 int = sstemp0 int
scoreboard players operation sstemp2 int *= float_int0 int
scoreboard players operation float_int1 int -= sstemp2 int
scoreboard players operation float_int1 int *= 10 int
scoreboard players operation sstemp1 int = float_int1 int
scoreboard players operation float_int1 int /= sstempd int
scoreboard players operation float_int1 int *= 40 int
scoreboard players operation sstemp1 int %= sstempd int
scoreboard players operation sstemp1 int *= 40 int
scoreboard players operation sstemp1 int /= sstempd int
scoreboard players operation float_int1 int += sstemp1 int
#符号
scoreboard players operation float_sign int = @s float_sign
#指数
scoreboard players operation float_exp int = @s float_exp
scoreboard players operation float_exp int *= -1 int
scoreboard players add float_exp int 1
#对齐小数点
execute if score float_int0 int matches 10000.. run function math:hpo/float/div_align

#---------
#高低位
scoreboard players operation sstemp0 int = 3vec_float_x_int0 int
scoreboard players operation sstemp0 int *= float_int1 int
scoreboard players operation sstemp1 int = 3vec_float_x_int1 int
scoreboard players operation sstemp1 int *= float_int1 int
scoreboard players operation sstemp1 int /= 10000 int
scoreboard players operation 3vec_float_x_int1 int *= float_int0 int
scoreboard players operation 3vec_float_x_int1 int += sstemp0 int
scoreboard players operation 3vec_float_x_int1 int += sstemp1 int
scoreboard players operation 3vec_float_x_int0 int *= float_int0 int
scoreboard players operation 3vec_float_x_int0 int *= 10 int
scoreboard players operation 3vec_float_x_int1 int /= 1000 int
scoreboard players operation 3vec_float_x_int0 int += 3vec_float_x_int1 int
#符号位
scoreboard players operation 3vec_float_x_sign int *= float_sign int
#指数位
scoreboard players operation 3vec_float_x_exp int += float_exp int
scoreboard players remove 3vec_float_x_exp int 1
#小数位对齐
execute if score 3vec_float_x_int0 int matches 100000000.. run function math:hpo/3vec_float/mult_alignx
scoreboard players operation 3vec_float_x_int1 int = 3vec_float_x_int0 int
scoreboard players operation 3vec_float_x_int0 int /= 10000 int
scoreboard players operation 3vec_float_x_int1 int %= 10000 int

#高低位
scoreboard players operation sstemp0 int = 3vec_float_y_int0 int
scoreboard players operation sstemp0 int *= float_int1 int
scoreboard players operation sstemp1 int = 3vec_float_y_int1 int
scoreboard players operation sstemp1 int *= float_int1 int
scoreboard players operation sstemp1 int /= 10000 int
scoreboard players operation 3vec_float_y_int1 int *= float_int0 int
scoreboard players operation 3vec_float_y_int1 int += sstemp0 int
scoreboard players operation 3vec_float_y_int1 int += sstemp1 int
scoreboard players operation 3vec_float_y_int0 int *= float_int0 int
scoreboard players operation 3vec_float_y_int0 int *= 10 int
scoreboard players operation 3vec_float_y_int1 int /= 1000 int
scoreboard players operation 3vec_float_y_int0 int += 3vec_float_y_int1 int
#符号位
scoreboard players operation 3vec_float_y_sign int *= float_sign int
#指数位
scoreboard players operation 3vec_float_y_exp int += float_exp int
scoreboard players remove 3vec_float_y_exp int 1
#小数位对齐
execute if score 3vec_float_y_int0 int matches 100000000.. run function math:hpo/3vec_float/mult_aligny
scoreboard players operation 3vec_float_y_int1 int = 3vec_float_y_int0 int
scoreboard players operation 3vec_float_y_int0 int /= 10000 int
scoreboard players operation 3vec_float_y_int1 int %= 10000 int

#高低位
scoreboard players operation sstemp0 int = 3vec_float_z_int0 int
scoreboard players operation sstemp0 int *= float_int1 int
scoreboard players operation sstemp1 int = 3vec_float_z_int1 int
scoreboard players operation sstemp1 int *= float_int1 int
scoreboard players operation sstemp1 int /= 10000 int
scoreboard players operation 3vec_float_z_int1 int *= float_int0 int
scoreboard players operation 3vec_float_z_int1 int += sstemp0 int
scoreboard players operation 3vec_float_z_int1 int += sstemp1 int
scoreboard players operation 3vec_float_z_int0 int *= float_int0 int
scoreboard players operation 3vec_float_z_int0 int *= 10 int
scoreboard players operation 3vec_float_z_int1 int /= 1000 int
scoreboard players operation 3vec_float_z_int0 int += 3vec_float_z_int1 int
#符号位
scoreboard players operation 3vec_float_z_sign int *= float_sign int
#指数位
scoreboard players operation 3vec_float_z_exp int += float_exp int
scoreboard players remove 3vec_float_z_exp int 1
#小数位对齐
execute if score 3vec_float_z_int0 int matches 100000000.. run function math:hpo/3vec_float/mult_alignz
scoreboard players operation 3vec_float_z_int1 int = 3vec_float_z_int0 int
scoreboard players operation 3vec_float_z_int0 int /= 10000 int
scoreboard players operation 3vec_float_z_int1 int %= 10000 int