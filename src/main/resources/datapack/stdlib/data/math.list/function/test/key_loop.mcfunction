#math.list:test/key_loop
data modify storage math:io stemp.key set from storage math:io result[0]
data modify storage math:io list append from storage math:io stemp
#循环迭代
data remove storage math:io result[0]
scoreboard players remove loop int 1
execute if score loop int matches 1.. run function math.list:test/key_loop