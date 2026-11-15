scoreboard players operation float_sign int = mr_low_sign int
scoreboard players operation float_int0 int = mr_low_int0 int
scoreboard players operation float_int1 int = mr_low_int1 int
scoreboard players operation float_exp int = mr_low_exp int
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_sign = mr_high_sign int
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_int0 = mr_high_int0 int
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_int1 = mr_high_int1 int
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_exp = mr_high_exp int
execute as 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 run function math.float:hpo/float/_add
scoreboard players set mr_two_sign int 1
scoreboard players set mr_two_int0 int 2000
scoreboard players set mr_two_int1 int 0
scoreboard players set mr_two_exp int 1
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_sign = mr_two_sign int
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_int0 = mr_two_int0 int
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_int1 = mr_two_int1 int
scoreboard players operation 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 float_exp = mr_two_exp int
execute as 53aa19cc-a067-402b-8ba1-9328cc5fb6c1 run function math.float:hpo/float/_div
scoreboard players operation mr_mid_sign int = float_sign int
scoreboard players operation mr_mid_int0 int = float_int0 int
scoreboard players operation mr_mid_int1 int = float_int1 int
scoreboard players operation mr_mid_exp int = float_exp int
scoreboard players operation mf_n int = mr_n int
function math.float:hpo/float/pow
scoreboard players set mr_less int 0
execute if score float_exp int < mr_input_exp int run scoreboard players set mr_less int 1
execute if score float_exp int = mr_input_exp int if score float_int0 int < mr_input_int0 int run scoreboard players set mr_less int 1
execute if score float_exp int = mr_input_exp int if score float_int0 int = mr_input_int0 int if score float_int1 int <= mr_input_int1 int run scoreboard players set mr_less int 1
execute if score mr_less int matches 1 run scoreboard players operation mr_low_sign int = mr_mid_sign int
execute if score mr_less int matches 1 run scoreboard players operation mr_low_int0 int = mr_mid_int0 int
execute if score mr_less int matches 1 run scoreboard players operation mr_low_int1 int = mr_mid_int1 int
execute if score mr_less int matches 1 run scoreboard players operation mr_low_exp int = mr_mid_exp int
execute if score mr_less int matches 0 run scoreboard players operation mr_high_sign int = mr_mid_sign int
execute if score mr_less int matches 0 run scoreboard players operation mr_high_int0 int = mr_mid_int0 int
execute if score mr_less int matches 0 run scoreboard players operation mr_high_int1 int = mr_mid_int1 int
execute if score mr_less int matches 0 run scoreboard players operation mr_high_exp int = mr_mid_exp int
scoreboard players remove mr_iterations int 1
execute if score mr_iterations int matches 1.. run function math.float:hpo/float/nroot_loop
