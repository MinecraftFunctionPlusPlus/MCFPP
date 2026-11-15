scoreboard players operation mf_negative int = mf_n int
scoreboard players operation mf_base_sign int = float_sign int
scoreboard players operation mf_base_int0 int = float_int0 int
scoreboard players operation mf_base_int1 int = float_int1 int
scoreboard players operation mf_base_exp int = float_exp int
scoreboard players set mf_result_sign int 1
scoreboard players set mf_result_int0 int 1000
scoreboard players set mf_result_int1 int 0
scoreboard players set mf_result_exp int 1
execute if score mf_n int matches -2147483648 run function math.float:hpo/float/pow_min
execute if score mf_n int matches -2147483647..-1 run scoreboard players operation mf_n int *= -1 int
execute if score mf_n int matches 1.. run function math.float:hpo/float/pow_loop
scoreboard players operation float_sign int = mf_result_sign int
scoreboard players operation float_int0 int = mf_result_int0 int
scoreboard players operation float_int1 int = mf_result_int1 int
scoreboard players operation float_exp int = mf_result_exp int
execute if score mf_negative int matches ..-1 run function math.float:hpo/float/pow_inverse
execute if score mf_base_sign int matches 0 if score mf_negative int matches ..0 run function math.float:hpo/float/zero
