data modify storage math.3vec_float:io stemp set value {list_zero:[]}
execute if score 3vec_float_z_sign int matches 0.. run data modify storage math.3vec_float:io stemp.sign set value ""
execute if score 3vec_float_z_sign int matches -1 run data modify storage math.3vec_float:io stemp.sign set value "-"
execute if score 3vec_float_z_int1 int matches ..999 run data modify storage math.3vec_float:io stemp.list_zero append value "0"
execute if score 3vec_float_z_int1 int matches ..99 run data modify storage math.3vec_float:io stemp.list_zero append value "0"
execute if score 3vec_float_z_int1 int matches ..9 run data modify storage math.3vec_float:io stemp.list_zero append value "0"
tellraw @a ["(float)z: ",{"nbt":"stemp.sign","storage":"math.3vec_float:io"},"0.",{"score":{"name":"3vec_float_z_int0","objective":"int"}},{"nbt":"stemp.list_zero","storage":"math.3vec_float:io","interpret":true},{"score":{"name":"3vec_float_z_int1","objective":"int"}},", E",{"score":{"name":"3vec_float_z_exp","objective":"int"}}]