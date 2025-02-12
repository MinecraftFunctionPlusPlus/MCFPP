#math:hpo/3vec_float/_tovec

#x分量
scoreboard players operation 3vec_x int = 3vec_float_x_int0 int
scoreboard players operation 3vec_x int *= 10000 int
scoreboard players operation 3vec_x int += 3vec_float_x_int1 int
execute if score 3vec_float_x_exp int matches 3.. run function math:hpo/3vec_float/tovecx/3_6
execute if score 3vec_float_x_exp int matches 1..2 run function math:hpo/3vec_float/tovecx/1_2
execute if score 3vec_float_x_exp int matches -1..0 run function math:hpo/3vec_float/tovecx/-1_0
execute if score 3vec_float_x_exp int matches ..-2 run function math:hpo/3vec_float/tovecx/-4_-2
scoreboard players operation 3vec_x int *= 3vec_float_x_sign int

#y分量
scoreboard players operation 3vec_y int = 3vec_float_y_int0 int
scoreboard players operation 3vec_y int *= 10000 int
scoreboard players operation 3vec_y int += 3vec_float_y_int1 int
execute if score 3vec_float_y_exp int matches 3.. run function math:hpo/3vec_float/tovecy/3_6
execute if score 3vec_float_y_exp int matches 1..2 run function math:hpo/3vec_float/tovecy/1_2
execute if score 3vec_float_y_exp int matches -1..0 run function math:hpo/3vec_float/tovecy/-1_0
execute if score 3vec_float_y_exp int matches ..-2 run function math:hpo/3vec_float/tovecy/-4_-2
scoreboard players operation 3vec_y int *= 3vec_float_y_sign int

#z分量
scoreboard players operation 3vec_z int = 3vec_float_z_int0 int
scoreboard players operation 3vec_z int *= 10000 int
scoreboard players operation 3vec_z int += 3vec_float_z_int1 int
execute if score 3vec_float_z_exp int matches 3.. run function math:hpo/3vec_float/tovecz/3_6
execute if score 3vec_float_z_exp int matches 1..2 run function math:hpo/3vec_float/tovecz/1_2
execute if score 3vec_float_z_exp int matches -1..0 run function math:hpo/3vec_float/tovecz/-1_0
execute if score 3vec_float_z_exp int matches ..-2 run function math:hpo/3vec_float/tovecz/-4_-2
scoreboard players operation 3vec_z int *= 3vec_float_z_sign int