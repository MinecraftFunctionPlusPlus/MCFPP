#math.list:_shuffle
execute store result score sinp int if data storage math:io list[]
function math:stmp/_get

execute as @e[tag=stmp] run function math.list:shuffle/get
execute as @e[tag=stmp,sort=random] run data modify storage math:io list append from entity @s data.stemp

kill @e[type=marker,tag=stmp_kill]
tag @e[type=marker] remove stmp