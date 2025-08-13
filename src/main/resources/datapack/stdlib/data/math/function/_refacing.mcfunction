#math:_refacing
# 规整化执行者的朝向
# execute rotated 44.9 45.1 as entity run function math:_refacing
# 朝向被规整化为(0.0,90.0)

execute as 0-0-0-8593-0 run function math:3vec/_facingto
function math:3vec/_polar
execute if score 3vec_x int = 3vec_z int run scoreboard players set 3vec_x int 0
execute if score 3vec_x int = 3vec_y int run scoreboard players set 3vec_x int 0
execute if score 3vec_y int = 3vec_z int run scoreboard players set 3vec_y int 0
execute as 0-0-0-8593-0 run function math:3vec/_tofacing
execute at @s rotated as 0-0-0-8593-0 run tp @s ~ ~ ~ ~ ~