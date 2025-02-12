#math:hpo/float/_ssrandnew
#随机赋值临时对象
scoreboard players set min int 0
scoreboard players set max int 1
function math:_random
scoreboard players operation random int *= 2 int
scoreboard players remove random int 1
scoreboard players operation float_sign int = random int

scoreboard players set min int 10000000
scoreboard players set max int 99999999
function math:_random
execute store result score float_int1 int run scoreboard players operation float_int0 int = random int
scoreboard players operation float_int0 int /= 10000 int
scoreboard players operation float_int1 int %= 10000 int

scoreboard players set min int -1000
scoreboard players set max int 1000000
function math:_random
scoreboard players operation float_exp int = random int