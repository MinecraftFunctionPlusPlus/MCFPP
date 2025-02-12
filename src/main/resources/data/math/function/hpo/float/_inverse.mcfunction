#math:hpo/float/_inverse

#由万进制节计算除数和被除数
scoreboard players operation sstempd int = float_int0 int
scoreboard players operation sstempd int *= 10000 int
scoreboard players operation sstempd int += float_int1 int
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

#指数
scoreboard players operation float_exp int *= -1 int
scoreboard players add float_exp int 1
#对齐小数点
execute if score float_int0 int matches 10000.. run function math:hpo/float/div_align