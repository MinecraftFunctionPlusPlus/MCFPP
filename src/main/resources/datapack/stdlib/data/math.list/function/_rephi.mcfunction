#math.list:_rephi
#获得相对转动
execute store result score stemp1 int store result score stempn int if data storage math:io list[]
scoreboard players operation stemp0 int = list_phi int
scoreboard players operation stemp0 int *= -1 int
scoreboard players operation stemp0 int %= stempn int

#抄近道
scoreboard players operation stemp1 int /= 2 int
execute if score stemp0 int >= stemp1 int run scoreboard players operation stemp0 int -= stempn int

#进入转动循环
execute if score stemp0 int matches 1.. run function math.list:rot_loop0
execute if score stemp0 int matches ..-1 run function math.list:rot_loop1

#更新列表相位
scoreboard players set list_phi int 0