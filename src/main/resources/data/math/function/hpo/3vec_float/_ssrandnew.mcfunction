#math:hpo/3vec_float/_ssrandnew
#需要传入世界实体为执行者

scoreboard players set min int -1800000
scoreboard players set max int 1800000
function math:_random
execute store result entity @s Rotation[0] float 0.0001 run scoreboard players get random int

scoreboard players set min int -900000
scoreboard players set max int 900000
function math:_random
execute store result entity @s Rotation[1] float 0.0001 run scoreboard players get random int

scoreboard players set min int 6400
scoreboard players set max int 32000
function math:_random
execute at @s positioned 0.0 0.0 0.0 run tp @s ^ ^ ^1.0
execute store result score 3vec_x int run data get entity @s Pos[0] 10000
execute store result score 3vec_y int run data get entity @s Pos[1] 10000
execute store result score 3vec_z int run data get entity @s Pos[2] 10000
scoreboard players operation 3vec_x int *= random int
scoreboard players operation 3vec_x int /= 10000 int
scoreboard players operation 3vec_y int *= random int
scoreboard players operation 3vec_y int /= 10000 int
scoreboard players operation 3vec_z int *= random int
scoreboard players operation 3vec_z int /= 10000 int
function math:hpo/3vec_float/_vecto