# Freeze zero dispatch before changing work; @s remains read-only.
scoreboard players set numeric_active int 1
execute if score float_sign int matches 0 run scoreboard players set numeric_active int 0
execute if score @s float_sign matches 0 run scoreboard players set numeric_active int 0
execute if score numeric_active int matches 0 run function math.float:hpo/float/zero
execute if score numeric_active int matches 1 run function math.float:hpo/float/mult/nonzero
