execute if score float_exp int matches 1 run scoreboard players set mr_factor int 10000000
execute if score float_exp int matches 2 run scoreboard players set mr_factor int 1000000
execute if score float_exp int matches 3 run scoreboard players set mr_factor int 100000
execute if score float_exp int matches 4 run scoreboard players set mr_factor int 10000
execute if score float_exp int matches 5 run scoreboard players set mr_factor int 1000
execute if score float_exp int matches 6 run scoreboard players set mr_factor int 100
execute if score float_exp int matches 7 run scoreboard players set mr_factor int 10
scoreboard players operation mr_remainder int = mr_m int
scoreboard players operation mr_remainder int %= mr_factor int
scoreboard players operation mr_m int -= mr_remainder int
scoreboard players set mr_up int 0
execute if score mr_mode int matches 1 if score float_sign int matches -1 if score mr_remainder int matches 1.. run scoreboard players set mr_up int 1
execute if score mr_mode int matches 2 if score float_sign int matches 1 if score mr_remainder int matches 1.. run scoreboard players set mr_up int 1
scoreboard players operation mr_half int = mr_factor int
scoreboard players operation mr_half int /= 2 int
execute if score mr_mode int matches 3 if score float_sign int matches 1 if score mr_remainder int >= mr_half int run scoreboard players set mr_up int 1
execute if score mr_mode int matches 3 if score float_sign int matches -1 if score mr_remainder int > mr_half int run scoreboard players set mr_up int 1
execute if score mr_up int matches 1 run scoreboard players operation mr_m int += mr_factor int
execute if score mr_m int matches 100000000.. run scoreboard players add float_exp int 1
execute if score mr_m int matches 100000000.. run scoreboard players operation mr_m int /= 10 int
scoreboard players operation float_int0 int = mr_m int
scoreboard players operation float_int0 int /= 10000 int
scoreboard players operation float_int1 int = mr_m int
scoreboard players operation float_int1 int %= 10000 int
execute if score mr_m int matches 0 run function math.float:hpo/float/zero
