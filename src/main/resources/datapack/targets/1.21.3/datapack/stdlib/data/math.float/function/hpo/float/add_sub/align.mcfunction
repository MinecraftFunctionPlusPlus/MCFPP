execute if score add_difference int matches 2 run scoreboard players operation add_divisor int = 10 int
execute if score add_difference int matches 3 run scoreboard players operation add_divisor int = 100 int
execute if score add_difference int matches 4 run scoreboard players operation add_divisor int = 1000 int
execute if score add_difference int matches 5 run scoreboard players operation add_divisor int = 10000 int
execute if score add_difference int matches 6 run scoreboard players operation add_divisor int = 100000 int
execute if score add_difference int matches 7 run scoreboard players operation add_divisor int = 1000000 int
execute if score add_difference int matches 8 run scoreboard players operation add_divisor int = 10000000 int
scoreboard players operation add_remainder int = add_small int
scoreboard players operation add_remainder int %= add_divisor int
execute if score add_remainder int matches 1.. run scoreboard players set add_sticky int 1
scoreboard players operation add_small int /= add_divisor int
