#math:3vec/_ex-xyz
# 扩展函数：对3vec进行直角坐标属性扩展
# 输入：3vec{<3vec_rot0,int>,<3vec_rot1,int>,<3vec_l,int>}
# 输出：3vec{<3vec_x,int>,<3vec_y,int>,<3vec_z,int>,<3vec_rot0,int>,<3vec_rot1,int>,<3vec_l,int>}

#获得单位方向向量
execute store result entity @s Rotation[0] float 0.0001 run scoreboard players get 3vec_rot0 int
execute store result entity @s Rotation[1] float 0.0001 run scoreboard players get 3vec_rot1 int
execute rotated as @s positioned 0.0 0.0 0.0 run tp @s ^ ^ ^1.0

#对单位向量的x分量乘模长得到x坐标
execute store result score 3vec_x int run data get entity @s Pos[0] 10000
scoreboard players operation stemp0 int = 3vec_l int
scoreboard players operation stemp1 int = 3vec_l int
scoreboard players operation stemp0 int /= 10000 int
scoreboard players operation stemp1 int %= 10000 int
scoreboard players operation stemp1 int *= 3vec_x int
scoreboard players operation stemp1 int /= 10000 int
scoreboard players operation 3vec_x int *= stemp0 int
scoreboard players operation 3vec_x int += stemp1 int

#对单位向量的y分量乘模长得到y坐标
execute store result score 3vec_y int run data get entity @s Pos[1] 10000
scoreboard players operation stemp0 int = 3vec_l int
scoreboard players operation stemp1 int = 3vec_l int
scoreboard players operation stemp0 int /= 10000 int
scoreboard players operation stemp1 int %= 10000 int
scoreboard players operation stemp1 int *= 3vec_y int
scoreboard players operation stemp1 int /= 10000 int
scoreboard players operation 3vec_y int *= stemp0 int
scoreboard players operation 3vec_y int += stemp1 int

#对单位向量的z分量乘模长得到z坐标
execute store result score 3vec_z int run data get entity @s Pos[2] 10000
scoreboard players operation stemp0 int = 3vec_l int
scoreboard players operation stemp1 int = 3vec_l int
scoreboard players operation stemp0 int /= 10000 int
scoreboard players operation stemp1 int %= 10000 int
scoreboard players operation stemp1 int *= 3vec_z int
scoreboard players operation stemp1 int /= 10000 int
scoreboard players operation 3vec_z int *= stemp0 int
scoreboard players operation 3vec_z int += stemp1 int