# Integer conversion, truncating discarded decimal digits.
scoreboard players set float_sign int 0
scoreboard players set float_exp int 0
scoreboard players set float_int0 int 0
scoreboard players set float_int1 int 0
scoreboard players operation float_magnitude int = inp int
execute if score inp int matches 1.. run scoreboard players set float_sign int 1
execute if score inp int matches ..-1 run scoreboard players set float_sign int -1
execute if score inp int matches -2147483647..-1 run scoreboard players operation float_magnitude int *= -1 int
scoreboard players operation float_int0 int = float_magnitude int
execute if score float_magnitude int matches 1..9 run scoreboard players set float_exp int 1
execute if score float_magnitude int matches 1..9 run scoreboard players operation float_int0 int *= 10000000 int
execute if score float_magnitude int matches 10..99 run scoreboard players set float_exp int 2
execute if score float_magnitude int matches 10..99 run scoreboard players operation float_int0 int *= 1000000 int
execute if score float_magnitude int matches 100..999 run scoreboard players set float_exp int 3
execute if score float_magnitude int matches 100..999 run scoreboard players operation float_int0 int *= 100000 int
execute if score float_magnitude int matches 1000..9999 run scoreboard players set float_exp int 4
execute if score float_magnitude int matches 1000..9999 run scoreboard players operation float_int0 int *= 10000 int
execute if score float_magnitude int matches 10000..99999 run scoreboard players set float_exp int 5
execute if score float_magnitude int matches 10000..99999 run scoreboard players operation float_int0 int *= 1000 int
execute if score float_magnitude int matches 100000..999999 run scoreboard players set float_exp int 6
execute if score float_magnitude int matches 100000..999999 run scoreboard players operation float_int0 int *= 100 int
execute if score float_magnitude int matches 1000000..9999999 run scoreboard players set float_exp int 7
execute if score float_magnitude int matches 1000000..9999999 run scoreboard players operation float_int0 int *= 10 int
execute if score float_magnitude int matches 10000000..99999999 run scoreboard players set float_exp int 8
execute if score float_magnitude int matches 100000000..999999999 run scoreboard players set float_exp int 9
execute if score float_magnitude int matches 100000000..999999999 run scoreboard players operation float_int0 int /= 10 int
execute if score float_magnitude int matches 1000000000..2147483647 run scoreboard players set float_exp int 10
execute if score float_magnitude int matches 1000000000..2147483647 run scoreboard players operation float_int0 int /= 100 int
scoreboard players operation float_int1 int = float_int0 int
scoreboard players operation float_int0 int /= 10000 int
scoreboard players operation float_int1 int %= 10000 int
execute if score inp int matches -2147483648 run scoreboard players set float_sign int -1
execute if score inp int matches -2147483648 run scoreboard players set float_int0 int 2147
execute if score inp int matches -2147483648 run scoreboard players set float_int1 int 4836
execute if score inp int matches -2147483648 run scoreboard players set float_exp int 10
