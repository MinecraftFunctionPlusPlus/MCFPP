scoreboard players operation mr_degree int = mr_input_exp int
scoreboard players remove mr_degree int 1
scoreboard players operation mr_degree int /= mr_n int
scoreboard players set mr_low_sign int 1
scoreboard players set mr_low_int0 int 1000
scoreboard players set mr_low_int1 int 0
scoreboard players set mr_low_exp int 1
scoreboard players operation mr_low_exp int = mr_degree int
scoreboard players add mr_low_exp int 1
scoreboard players operation mr_high_sign int = mr_low_sign int
scoreboard players operation mr_high_int0 int = mr_low_int0 int
scoreboard players operation mr_high_int1 int = mr_low_int1 int
scoreboard players operation mr_high_exp int = mr_low_exp int
scoreboard players add mr_high_exp int 1
scoreboard players set mr_iterations int 32
function math.float:hpo/float/nroot_loop
scoreboard players operation float_sign int = mr_mid_sign int
scoreboard players operation float_int0 int = mr_mid_int0 int
scoreboard players operation float_int1 int = mr_mid_int1 int
scoreboard players operation float_exp int = mr_mid_exp int
scoreboard players operation float_sign int = mr_input_sign int
