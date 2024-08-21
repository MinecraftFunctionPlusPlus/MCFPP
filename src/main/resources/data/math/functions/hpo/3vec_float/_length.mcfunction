#math:hpo/3vec_float/_length
#需要传入执行者

scoreboard players operation float_sign int = 3vec_float_x_sign int
scoreboard players operation float_int0 int = 3vec_float_x_int0 int
scoreboard players operation float_int1 int = 3vec_float_x_int1 int
scoreboard players operation float_exp int = 3vec_float_x_exp int
function math:hpo/float/_sqr
scoreboard players operation @s float_sign = 3vec_float_y_sign int
scoreboard players operation @s float_int0 = 3vec_float_y_int0 int
scoreboard players operation @s float_int1 = 3vec_float_y_int1 int
scoreboard players operation @s float_exp = 3vec_float_y_exp int
function math:hpo/float/_zsqr
function math:hpo/float/_add
scoreboard players operation @s float_sign = 3vec_float_z_sign int
scoreboard players operation @s float_int0 = 3vec_float_z_int0 int
scoreboard players operation @s float_int1 = 3vec_float_z_int1 int
scoreboard players operation @s float_exp = 3vec_float_z_exp int
function math:hpo/float/_zsqr
function math:hpo/float/_add

function math:hpo/float/_sqrt