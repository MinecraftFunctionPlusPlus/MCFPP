#math:hpo/init

#浮点数
scoreboard objectives add float_sign dummy
scoreboard objectives add float_int0 dummy
scoreboard objectives add float_int1 dummy
scoreboard objectives add float_exp dummy

#浮点数三维向量
scoreboard objectives add 3vec_float_x_sign dummy
scoreboard objectives add 3vec_float_x_int0 dummy
scoreboard objectives add 3vec_float_x_int1 dummy
scoreboard objectives add 3vec_float_x_exp dummy
scoreboard objectives add 3vec_float_y_sign dummy
scoreboard objectives add 3vec_float_y_int0 dummy
scoreboard objectives add 3vec_float_y_int1 dummy
scoreboard objectives add 3vec_float_y_exp dummy
scoreboard objectives add 3vec_float_z_sign dummy
scoreboard objectives add 3vec_float_z_int0 dummy
scoreboard objectives add 3vec_float_z_int1 dummy
scoreboard objectives add 3vec_float_z_exp dummy

#临时对象初始化
data modify storage math:io double set value 0.0d
data modify storage math:io float set value 0.0f