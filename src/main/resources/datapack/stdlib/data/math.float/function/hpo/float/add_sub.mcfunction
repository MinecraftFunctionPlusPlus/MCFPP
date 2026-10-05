# Freeze input signs before handling zero or choosing the larger magnitude.
scoreboard players operation add_left_sign int = float_sign int
scoreboard players operation add_right_sign int = @s float_sign
scoreboard players operation add_right_sign int *= add_operation int
scoreboard players set add_active int 1
execute if score add_left_sign int matches 0 run scoreboard players set add_active int 0
execute if score add_right_sign int matches 0 run scoreboard players set add_active int 0
execute if score add_left_sign int matches 0 run function math.float:hpo/float/add_sub/copy_right
execute if score add_active int matches 1 run function math.float:hpo/float/add_sub/nonzero
execute if score float_sign int matches 0 run scoreboard players set float_int0 int 0
execute if score float_sign int matches 0 run scoreboard players set float_int1 int 0
execute if score float_sign int matches 0 run scoreboard players set float_exp int 0
