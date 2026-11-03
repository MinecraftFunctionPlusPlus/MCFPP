# Truncate toward zero and saturate outside the integer range.
scoreboard players operation res int = float_int0 int
scoreboard players operation res int *= 10000 int
scoreboard players operation res int += float_int1 int
scoreboard players set float_overflow int 0
execute if score float_exp int matches 10 run execute if score res int matches 21474837.. run scoreboard players set float_overflow int 1
execute if score float_exp int matches 11.. run scoreboard players set float_overflow int 1
execute if score float_exp int matches ..0 run scoreboard players set res int 0
execute if score float_exp int matches 1 run scoreboard players operation res int /= 10000000 int
execute if score float_exp int matches 2 run scoreboard players operation res int /= 1000000 int
execute if score float_exp int matches 3 run scoreboard players operation res int /= 100000 int
execute if score float_exp int matches 4 run scoreboard players operation res int /= 10000 int
execute if score float_exp int matches 5 run scoreboard players operation res int /= 1000 int
execute if score float_exp int matches 6 run scoreboard players operation res int /= 100 int
execute if score float_exp int matches 7 run scoreboard players operation res int /= 10 int
execute if score float_exp int matches 9 run scoreboard players operation res int *= 10 int
execute if score float_exp int matches 10 run execute if score float_overflow int matches 0 run scoreboard players operation res int *= 100 int
scoreboard players operation res int *= float_sign int
execute if score float_overflow int matches 1 run scoreboard players set res int 2147483647
execute if score float_overflow int matches 1 run execute if score float_sign int matches ..-1 run scoreboard players set res int -2147483648
execute if score float_sign int matches 0 run scoreboard players set res int 0
