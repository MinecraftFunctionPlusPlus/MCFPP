#math:_sincos
# 计算正余弦
# 需要传入世界实体为执行者
# 输入<inp,int>
# 输出{<cos,int>,<sin,int>}
execute store result entity @s Rotation[0] float -0.0001 run scoreboard players get inp int
execute at @s positioned 0.0 0.0 0.0 rotated ~ 0.0 run tp @s ^ ^ ^1.0
execute store result score cos int run data get entity @s Pos[2] 10000
execute store result score sin int run data get entity @s Pos[0] 10000