#math:hpo/float/_mult
#低位
scoreboard players operation sstemp0 int = float_int0 int
scoreboard players operation sstemp0 int *= @s float_int1
scoreboard players operation sstemp1 int = float_int1 int
scoreboard players operation sstemp1 int *= @s float_int1
scoreboard players operation sstemp1 int /= 10000 int
scoreboard players operation float_int1 int *= @s float_int0
scoreboard players operation float_int1 int += sstemp0 int
scoreboard players operation float_int1 int += sstemp1 int

#高位
scoreboard players operation float_int0 int *= @s float_int0
scoreboard players operation float_int0 int *= 10 int
scoreboard players operation float_int1 int /= 1000 int
scoreboard players operation float_int0 int += float_int1 int

#符号位
scoreboard players operation float_sign int *= @s float_sign
#指数位
scoreboard players operation float_exp int += @s float_exp
scoreboard players remove float_exp int 1
#小数位对齐
execute if score float_int0 int matches 100000000.. run function math:hpo/float/mult_align

scoreboard players operation float_int1 int = float_int0 int
scoreboard players operation float_int0 int /= 10000 int
scoreboard players operation float_int1 int %= 10000 int