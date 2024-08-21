#math:3vec/_ex-rot
# 扩展函数：对3vec进行球坐标属性扩展
# 输入：3vec{<3vec_x,int>,<3vec_y,int>,<3vec_z,int>}
# 输出：3vec{<3vec_x,int>,<3vec_y,int>,<3vec_z,int>,<3vec_rot0,int>,<3vec_rot1,int>,<3vec_l,int>}

#获得单位方向向量以及朝向
execute store result entity @s Pos[0] double 0.0001 run scoreboard players get 3vec_x int
execute store result entity @s Pos[1] double 0.0001 run scoreboard players get 3vec_y int
execute store result entity @s Pos[2] double 0.0001 run scoreboard players get 3vec_z int
execute positioned 0.0 0.0 0.0 facing entity @s feet run tp @s ^ ^ ^1.0 ~ ~

#把朝向输出到方向角
execute store result score 3vec_rot0 int run data get entity @s Rotation[0] 10000
execute store result score 3vec_rot1 int run data get entity @s Rotation[1] 10000

#单位方向向量转换为临时分数
execute store result score stempi0 int run data get entity @s Pos[0] 10000
execute store result score stempi1 int run data get entity @s Pos[1] 10000
execute store result score stempi2 int run data get entity @s Pos[2] 10000

#求坐标的绝对值和
scoreboard players operation 3vec_l int = 3vec_x int
execute if score 3vec_x int matches ..-1 run scoreboard players operation 3vec_l int *= -1 int
execute if score 3vec_y int matches 1.. run scoreboard players operation 3vec_l int += 3vec_y int
execute if score 3vec_y int matches ..-1 run scoreboard players operation 3vec_l int -= 3vec_y int
execute if score 3vec_z int matches 1.. run scoreboard players operation 3vec_l int += 3vec_z int
execute if score 3vec_z int matches ..-1 run scoreboard players operation 3vec_l int -= 3vec_z int

#求单位向量坐标的绝对值和
execute if score stempi0 int matches ..-1 run scoreboard players operation stempi0 int *= -1 int
execute if score stempi1 int matches ..-1 run scoreboard players operation stempi1 int *= -1 int
execute if score stempi2 int matches ..-1 run scoreboard players operation stempi2 int *= -1 int
scoreboard players operation stempi0 int += stempi1 int
scoreboard players operation stempi0 int += stempi2 int

#绝对值相除获得模长
scoreboard players operation stemp0 int = 3vec_l int
scoreboard players operation 3vec_l int /= stempi0 int
scoreboard players operation 3vec_l int *= 10000 int
scoreboard players operation stemp0 int %= stempi0 int
scoreboard players operation stemp0 int *= 10000 int
scoreboard players operation stemp0 int /= stempi0 int
scoreboard players operation 3vec_l int += stemp0 int