#math:_sincos
# 计算正余弦
# 输入<theta,int>
# 输出{<cos,int>, <sin,int>}
# 需要以世界实体为执行者

execute store result entity @s Rotation[0] float -0.0001 run scoreboard players get theta int
execute at @s positioned 0.0 0.0 0.0 rotated ~ 0.0 run tp @s ^ ^ ^1.0
data modify storage math:io xyz set from entity @s Pos
execute store result score cos int run data get storage math:io xyz[2] 10000
execute store result score sin int run data get storage math:io xyz[0] 10000
