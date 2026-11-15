scoreboard players operation mp_odd int = mp_n int
scoreboard players operation mp_odd int %= 2 int
execute if score mp_odd int matches 1 run function math:scalar/ipow_multiply
scoreboard players operation mp_n int /= 2 int
execute if score mp_ok int matches 1 if score mp_n int matches 1.. run function math:scalar/ipow_square
execute if score mp_ok int matches 1 if score mp_n int matches 1.. run function math:scalar/ipow_loop
