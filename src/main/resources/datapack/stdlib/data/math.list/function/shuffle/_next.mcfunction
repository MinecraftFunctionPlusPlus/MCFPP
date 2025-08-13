#math.list:shuffle/_next
execute as @e[tag=stmp] run function math.list:shuffle/get
execute as @e[tag=stmp,sort=random] run data modify storage math:io list append from entity @s data.stemp