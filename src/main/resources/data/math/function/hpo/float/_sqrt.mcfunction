#math:hpo/float/_sqrt

scoreboard players operation float_int0 int *= 10000 int
scoreboard players operation float_int0 int += float_int1 int

scoreboard players operation sstemp0 int = float_exp int
scoreboard players operation sstemp0 int %= 2 int
execute if score sstemp0 int matches 1 run scoreboard players operation float_int0 int *= 10 int

#指数位
scoreboard players operation float_exp int /= 2 int

#高位低位
scoreboard players operation inp int = float_int0 int
function math:sqrt/_3sqrt
scoreboard players operation res int *= 10 int
scoreboard players operation float_int0 int = res int
#小数位对齐
execute if score float_int0 int matches 100000000.. run function math:hpo/float/mult_align

scoreboard players operation float_int1 int = float_int0 int
scoreboard players operation float_int0 int /= 10000 int
scoreboard players operation float_int1 int %= 10000 int