scoreboard players operation float_sign int = mf_result_sign int
scoreboard players operation float_int0 int = mf_result_int0 int
scoreboard players operation float_int1 int = mf_result_int1 int
scoreboard players operation float_exp int = mf_result_exp int
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_sign = mf_base_sign int
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_int0 = mf_base_int0 int
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_int1 = mf_base_int1 int
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_exp = mf_base_exp int
execute as 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 run function math.float:hpo/float/_mult
scoreboard players operation mf_result_sign int = float_sign int
scoreboard players operation mf_result_int0 int = float_int0 int
scoreboard players operation mf_result_int1 int = float_int1 int
scoreboard players operation mf_result_exp int = float_exp int
