#math:3vec/_facingto
#需要传入世界实体为执行者
execute positioned 0.0 0.0 0.0 run tp @s ^ ^ ^1.0
execute store result score 3vec_x int run data get entity @s Pos[0] 10000
execute store result score 3vec_y int run data get entity @s Pos[1] 10000
execute store result score 3vec_z int run data get entity @s Pos[2] 10000