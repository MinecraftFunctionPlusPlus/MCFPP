scoreboard players set mp_result int 1
scoreboard players set mp_ok int 1
execute if score mp_n int matches ..-1 run scoreboard players set mp_ok int 0
execute if score mp_base int matches 0 if score mp_n int matches 0 run scoreboard players set mp_ok int 0
execute if score mp_ok int matches 1 if score mp_n int matches 1.. run function math:scalar/ipow_loop
execute if score mp_ok int matches 0 run scoreboard players set mp_result int 0
