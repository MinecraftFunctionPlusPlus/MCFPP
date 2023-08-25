#math:hpo/float/_sqr
#低位
scoreboard players operation sstemp0 int = float_int1 int
scoreboard players operation sstemp0 int *= float_int1 int
scoreboard players operation sstemp0 int /= 10000 int
scoreboard players operation float_int1 int *= float_int0 int
scoreboard players operation float_int1 int *= 2 int
scoreboard players operation float_int1 int += sstemp0 int

#高位
scoreboard players operation float_int0 int *= float_int0 int
scoreboard players operation float_int0 int *= 10 int
scoreboard players operation float_int1 int /= 1000 int
scoreboard players operation float_int0 int += float_int1 int

#符号位
scoreboard players operation float_sign int *= float_sign int
#指数位
scoreboard players operation float_exp int *= 2 int
scoreboard players remove float_exp int 1
#小数位对齐
execute if score float_int0 int matches 100000000.. run function math:hpo/float/mult_align

scoreboard players operation float_int1 int = float_int0 int
scoreboard players operation float_int0 int /= 10000 int
scoreboard players operation float_int1 int %= 10000 int