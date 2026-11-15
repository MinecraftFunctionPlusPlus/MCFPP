scoreboard players set mr_up int 0
execute if score mr_mode int matches 1 if score float_sign int matches -1 run scoreboard players set mr_up int 1
execute if score mr_mode int matches 2 if score float_sign int matches 1 run scoreboard players set mr_up int 1
execute if score mr_mode int matches 3 if score float_exp int matches 0 if score float_sign int matches 1 if score mr_m int matches 50000000.. run scoreboard players set mr_up int 1
execute if score mr_mode int matches 3 if score float_exp int matches 0 if score float_sign int matches -1 if score mr_m int matches 50000001.. run scoreboard players set mr_up int 1
execute if score mr_up int matches 1 run scoreboard players set float_int0 int 1000
execute if score mr_up int matches 1 run scoreboard players set float_int1 int 0
execute if score mr_up int matches 1 run scoreboard players set float_exp int 1
execute if score mr_up int matches 0 run function math.float:hpo/float/zero
