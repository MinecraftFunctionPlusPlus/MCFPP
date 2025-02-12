#math:hpo/float/_cmp
#返回比较大小结果到<res,int>，返回绝对值比较大小结果到<sres,int>
scoreboard players set sres int 0
execute if score @s float_exp > float_exp int run scoreboard players set sres int 1
execute if score @s float_exp < float_exp int run scoreboard players set sres int -1
execute if score sres int matches 0 if score @s float_int0 > float_int0 int run scoreboard players set sres int 1
execute if score sres int matches 0 if score @s float_int0 < float_int0 int run scoreboard players set sres int -1
execute if score sres int matches 0 if score @s float_int1 > float_int1 int run scoreboard players set sres int 1
execute if score sres int matches 0 if score @s float_int1 < float_int1 int run scoreboard players set sres int -1

scoreboard players operation res int = sres int
execute if score @s float_sign = float_sign int if score float_sign int matches -1 run scoreboard players operation res int *= -1 int
execute if score @s float_sign > float_sign int run scoreboard players set res int 1
execute if score @s float_sign < float_sign int run scoreboard players set res int -1