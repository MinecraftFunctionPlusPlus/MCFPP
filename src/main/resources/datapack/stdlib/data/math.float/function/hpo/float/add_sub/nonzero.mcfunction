# Tail signs are applied only after unsigned alignment.
scoreboard players operation add_relative_sign int = add_left_sign int
scoreboard players operation add_relative_sign int *= add_right_sign int
scoreboard players operation float_int0 int *= 10000 int
scoreboard players operation float_int0 int += float_int1 int
scoreboard players operation add_small int = @s float_int0
scoreboard players operation add_small int *= 10000 int
scoreboard players operation add_small int += @s float_int1
scoreboard players set add_swap int 1
execute if score float_exp int > @s float_exp run scoreboard players set add_swap int 0
execute if score float_exp int = @s float_exp if score float_int0 int >= add_small int run scoreboard players set add_swap int 0
scoreboard players operation add_difference int = float_exp int
scoreboard players operation add_difference int -= @s float_exp
execute if score add_swap int matches 1 run function math.float:hpo/float/add_sub/swap
# One guard digit keeps cancellation precise; the maximum work value is 1999999980.
scoreboard players operation float_int0 int *= 10 int
scoreboard players remove float_exp int 1
scoreboard players set add_sticky int 0
execute if score add_difference int matches 0 run scoreboard players operation add_small int *= 10 int
execute if score add_difference int matches 2..8 run function math.float:hpo/float/add_sub/align
execute if score add_difference int matches 9.. run scoreboard players set add_sticky int 1
execute if score add_difference int matches 9.. run scoreboard players set add_small int 0
scoreboard players operation add_small int *= add_relative_sign int
scoreboard players operation float_int0 int += add_small int
execute if score add_relative_sign int matches -1 if score add_sticky int matches 1 run scoreboard players remove float_int0 int 1
function math.float:hpo/float/add_sub/normalize
scoreboard players operation float_int1 int = float_int0 int
scoreboard players operation float_int0 int /= 10000 int
scoreboard players operation float_int1 int %= 10000 int
