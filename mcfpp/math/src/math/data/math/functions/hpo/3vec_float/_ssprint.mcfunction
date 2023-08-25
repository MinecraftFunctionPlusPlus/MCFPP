#math:hpo/3vec_float/_ssprint
tellraw @a "---3vec_float---"

data modify storage math:io stemp set value {list_zero:[]}
execute if score 3vec_float_x_sign int matches 0.. run data modify storage math:io stemp.sign set value ""
execute if score 3vec_float_x_sign int matches -1 run data modify storage math:io stemp.sign set value "-"
execute if score 3vec_float_x_int1 int matches ..999 run data modify storage math:io stemp.list_zero append value "0"
execute if score 3vec_float_x_int1 int matches ..99 run data modify storage math:io stemp.list_zero append value "0"
execute if score 3vec_float_x_int1 int matches ..9 run data modify storage math:io stemp.list_zero append value "0"
tellraw @a ["(float)x: ",{"nbt":"stemp.sign","storage":"math:io"},"0.",{"score":{"name":"3vec_float_x_int0","objective":"int"}},{"nbt":"stemp.list_zero","storage":"math:io","interpret":true},{"score":{"name":"3vec_float_x_int1","objective":"int"}},", E",{"score":{"name":"3vec_float_x_exp","objective":"int"}}]

data modify storage math:io stemp set value {list_zero:[]}
execute if score 3vec_float_y_sign int matches 0.. run data modify storage math:io stemp.sign set value ""
execute if score 3vec_float_y_sign int matches -1 run data modify storage math:io stemp.sign set value "-"
execute if score 3vec_float_y_int1 int matches ..999 run data modify storage math:io stemp.list_zero append value "0"
execute if score 3vec_float_y_int1 int matches ..99 run data modify storage math:io stemp.list_zero append value "0"
execute if score 3vec_float_y_int1 int matches ..9 run data modify storage math:io stemp.list_zero append value "0"
tellraw @a ["(float)y: ",{"nbt":"stemp.sign","storage":"math:io"},"0.",{"score":{"name":"3vec_float_y_int0","objective":"int"}},{"nbt":"stemp.list_zero","storage":"math:io","interpret":true},{"score":{"name":"3vec_float_y_int1","objective":"int"}},", E",{"score":{"name":"3vec_float_y_exp","objective":"int"}}]

data modify storage math:io stemp set value {list_zero:[]}
execute if score 3vec_float_z_sign int matches 0.. run data modify storage math:io stemp.sign set value ""
execute if score 3vec_float_z_sign int matches -1 run data modify storage math:io stemp.sign set value "-"
execute if score 3vec_float_z_int1 int matches ..999 run data modify storage math:io stemp.list_zero append value "0"
execute if score 3vec_float_z_int1 int matches ..99 run data modify storage math:io stemp.list_zero append value "0"
execute if score 3vec_float_z_int1 int matches ..9 run data modify storage math:io stemp.list_zero append value "0"
tellraw @a ["(float)z: ",{"nbt":"stemp.sign","storage":"math:io"},"0.",{"score":{"name":"3vec_float_z_int0","objective":"int"}},{"nbt":"stemp.list_zero","storage":"math:io","interpret":true},{"score":{"name":"3vec_float_z_int1","objective":"int"}},", E",{"score":{"name":"3vec_float_z_exp","objective":"int"}}]