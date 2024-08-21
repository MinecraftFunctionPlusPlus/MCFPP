#math:hpo/float/_div

#由万进制节计算除数和被除数
scoreboard players operation float_int0 int *= 10000 int
scoreboard players operation float_int0 int += float_int1 int
scoreboard players operation float_int0 int *= 10 int
scoreboard players operation sstempd int = @s float_int0
scoreboard players operation sstempd int *= 10000 int
scoreboard players operation sstempd int += @s float_int1

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

#对齐小数点
execute if score float_int0 int matches 10000.. run function math:hpo/float/div_align

#符号位
scoreboard players operation float_sign int *= @s float_sign
#指数
scoreboard players operation float_exp int -= @s float_exp
#0的指数校准
execute if score float_sign int matches 0 run scoreboard players set float_exp int 0