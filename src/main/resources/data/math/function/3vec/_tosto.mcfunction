#math:3vec/_tosto
#转换函数：将3vec转换为nbt形式
#输入：3vec{<3vec_x,int>,<3vec_y,int>,<3vec_z,int>,<3vec_rot0,int>,<3vec_rot1,int>,<3vec_l,int>,<3vec_n,int>}
#输出：nbt_3vec{[3list_double,3vec.xyz],[3list_double,3vec.rot],[int,3vec.n]}
execute store result storage math:io 3vec.xyz[0] double 0.0001 run scoreboard players get 3vec_x int
execute store result storage math:io 3vec.xyz[1] double 0.0001 run scoreboard players get 3vec_y int
execute store result storage math:io 3vec.xyz[2] double 0.0001 run scoreboard players get 3vec_z int

execute store result storage math:io 3vec.rot[0] double 0.0001 run scoreboard players get 3vec_rot0 int
execute store result storage math:io 3vec.rot[1] double 0.0001 run scoreboard players get 3vec_rot1 int
execute store result storage math:io 3vec.rot[2] double 0.0001 run scoreboard players get 3vec_l int

execute store result storage math:io 3vec.n int 1 run scoreboard players get 3vec_n int