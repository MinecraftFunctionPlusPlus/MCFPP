scoreboard players operation mf_odd int = mf_n int
scoreboard players operation mf_odd int %= 2 int
execute if score mf_odd int matches 1 run function math.float:hpo/float/pow_multiply
scoreboard players operation mf_n int /= 2 int
execute if score mf_n int matches 1.. run function math.float:hpo/float/pow_square
execute if score mf_n int matches 1.. run function math.float:hpo/float/pow_loop
