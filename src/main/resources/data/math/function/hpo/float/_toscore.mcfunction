#math:hpo/float/_toscore

scoreboard players operation res int = float_int0 int
scoreboard players operation res int *= 10000 int
scoreboard players operation res int += float_int1 int

execute if score float_exp int matches 3.. run function math:hpo/float/toscore/3_6
execute if score float_exp int matches 1..2 run function math:hpo/float/toscore/1_2
execute if score float_exp int matches -1..0 run function math:hpo/float/toscore/-1_0
execute if score float_exp int matches ..-2 run function math:hpo/float/toscore/-4_-2

scoreboard players operation res int *= float_sign int