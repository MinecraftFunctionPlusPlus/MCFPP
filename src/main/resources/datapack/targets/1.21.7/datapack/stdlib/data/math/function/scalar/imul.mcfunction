scoreboard players operation mp_product int = mp_left int
scoreboard players operation mp_product int *= mp_right int
scoreboard players operation mp_check int = mp_product int
execute unless score mp_left int matches 0 run scoreboard players operation mp_check int /= mp_left int
execute unless score mp_left int matches 0 unless score mp_check int = mp_right int run scoreboard players set mp_ok int 0
execute if score mp_left int matches -1 if score mp_right int matches -2147483648 run scoreboard players set mp_ok int 0
