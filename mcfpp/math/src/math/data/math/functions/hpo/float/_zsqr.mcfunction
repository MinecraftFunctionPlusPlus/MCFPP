#math:hpo/float/_zsqr
#低位
scoreboard players operation sstemp0 int = @s float_int1
scoreboard players operation sstemp0 int *= @s float_int1
scoreboard players operation sstemp0 int /= 10000 int
scoreboard players operation @s float_int1 *= @s float_int0
scoreboard players operation @s float_int1 *= 2 int
scoreboard players operation @s float_int1 += sstemp0 int

#高位
scoreboard players operation @s float_int0 *= @s float_int0
scoreboard players operation @s float_int0 *= 10 int
scoreboard players operation @s float_int1 /= 1000 int
scoreboard players operation @s float_int0 += @s float_int1

#符号位
scoreboard players operation @s float_sign *= @s float_sign
#指数位
scoreboard players operation @s float_exp *= 2 int
scoreboard players remove @s float_exp 1
#小数位对齐
execute if score @s float_int0 matches 100000000.. run function math:hpo/float/zmult_align

scoreboard players operation @s float_int1 = @s float_int0
scoreboard players operation @s float_int0 /= 10000 int
scoreboard players operation @s float_int1 %= 10000 int