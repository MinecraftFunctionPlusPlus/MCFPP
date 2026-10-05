# Positive work magnitude; select wide cases before the shared eight-digit leaves.
execute if score float_int0 int matches 1000000000.. run scoreboard players add float_exp int 2
execute if score float_int0 int matches 1000000000.. run scoreboard players operation float_int0 int /= 100 int
execute if score float_int0 int matches 100000000..999999999 run scoreboard players add float_exp int 1
execute if score float_int0 int matches 100000000..999999999 run scoreboard players operation float_int0 int /= 10 int
execute if score float_int0 int matches 100000..99999999 run function math.float:hpo/float/add_search/align_s2
execute if score float_int0 int matches 100..99999 run function math.float:hpo/float/add_search/align_s1
execute if score float_int0 int matches 0..99 run function math.float:hpo/float/add_search/align_s0
