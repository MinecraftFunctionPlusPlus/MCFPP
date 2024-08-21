#math:3vec/_cross
scoreboard players operation stempx int = 3vec_x int
scoreboard players operation 3vec_x int = 3vec_y int
scoreboard players operation 3vec_x int *= @s 3vec_z
scoreboard players operation stemp0 int = @s 3vec_y
scoreboard players operation stemp0 int *= 3vec_z int
scoreboard players operation 3vec_x int -= stemp0 int
scoreboard players operation 3vec_x int /= 10000 int

scoreboard players operation stempy int = 3vec_y int
scoreboard players operation 3vec_y int = 3vec_z int
scoreboard players operation 3vec_y int *= @s 3vec_x
scoreboard players operation stemp0 int = @s 3vec_z
scoreboard players operation stemp0 int *= stempx int
scoreboard players operation 3vec_y int -= stemp0 int
scoreboard players operation 3vec_y int /= 10000 int

scoreboard players operation 3vec_z int = stempx int
scoreboard players operation 3vec_z int *= @s 3vec_y
scoreboard players operation stemp0 int = @s 3vec_x
scoreboard players operation stemp0 int *= stempy int
scoreboard players operation 3vec_z int -= stemp0 int
scoreboard players operation 3vec_z int /= 10000 int