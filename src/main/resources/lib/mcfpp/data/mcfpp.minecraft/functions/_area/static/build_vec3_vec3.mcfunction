#Area()
#expression: area.startX=start[0]
data modify storage system.mcfpp.minecraft.stack_frame[0].area set value {endZ:0,endY:0,endX:0,startY:0,startX:0,startZ:0}
execute store result storage mcfpp:system storage system.mcfpp.minecraft.stack_frame[0].area.startX int 1 run scoreboard players operation area_startX mcfpp_default = mcfpp.minecraft_func_build_start$0 mcfpp_default
#expression end: area.startX=start[0]
#expression: area.startY=start[1]
execute store result storage mcfpp:system storage system.mcfpp.minecraft.stack_frame[0].area.startY int 1 run scoreboard players operation area_startY mcfpp_default = mcfpp.minecraft_func_build_start$1 mcfpp_default
#expression end: area.startY=start[1]
#expression: area.startZ=start[2]
execute store result storage mcfpp:system storage system.mcfpp.minecraft.stack_frame[0].area.startZ int 1 run scoreboard players operation area_startZ mcfpp_default = mcfpp.minecraft_func_build_start$2 mcfpp_default
#expression end: area.startZ=start[2]
#expression: area.endX=end[0]
execute store result storage mcfpp:system storage system.mcfpp.minecraft.stack_frame[0].area.endX int 1 run scoreboard players operation area_endX mcfpp_default = mcfpp.minecraft_func_build_end$0 mcfpp_default
#expression end: area.endX=end[0]
#expression: area.endY=end[1]
execute store result storage mcfpp:system storage system.mcfpp.minecraft.stack_frame[0].area.endY int 1 run scoreboard players operation area_endY mcfpp_default = mcfpp.minecraft_func_build_end$1 mcfpp_default
#expression end: area.endY=end[1]
#expression: area.endZ=end[2]
execute store result storage mcfpp:system storage system.mcfpp.minecraft.stack_frame[0].area.endZ int 1 run scoreboard players operation area_endZ mcfpp_default = mcfpp.minecraft_func_build_end$2 mcfpp_default
#expression end: area.endZ=end[2]
