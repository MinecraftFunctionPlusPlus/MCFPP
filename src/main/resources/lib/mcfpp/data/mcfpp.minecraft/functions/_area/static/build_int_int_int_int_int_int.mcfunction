#Area()
#expression: area.startX=sx
data modify storage system.mcfpp.minecraft.stack_frame[0].area set value {endZ:0,endY:0,endX:0,startY:0,startX:0,startZ:0}
execute store result storage mcfpp:system storage system.mcfpp.minecraft.stack_frame[0].area.startX int 1 run scoreboard players operation area_startX mcfpp_default = mcfpp.minecraft_func_build_sx mcfpp_default
#expression end: area.startX=sx
#expression: area.startY=sy
execute store result storage mcfpp:system storage system.mcfpp.minecraft.stack_frame[0].area.startY int 1 run scoreboard players operation area_startY mcfpp_default = mcfpp.minecraft_func_build_sy mcfpp_default
#expression end: area.startY=sy
#expression: area.startZ=sz
execute store result storage mcfpp:system storage system.mcfpp.minecraft.stack_frame[0].area.startZ int 1 run scoreboard players operation area_startZ mcfpp_default = mcfpp.minecraft_func_build_sz mcfpp_default
#expression end: area.startZ=sz
#expression: area.endX=ex
execute store result storage mcfpp:system storage system.mcfpp.minecraft.stack_frame[0].area.endX int 1 run scoreboard players operation area_endX mcfpp_default = mcfpp.minecraft_func_build_ex mcfpp_default
#expression end: area.endX=ex
#expression: area.endY=ey
execute store result storage mcfpp:system storage system.mcfpp.minecraft.stack_frame[0].area.endY int 1 run scoreboard players operation area_endY mcfpp_default = mcfpp.minecraft_func_build_ey mcfpp_default
#expression end: area.endY=ey
#expression: area.endZ=ez
execute store result storage mcfpp:system storage system.mcfpp.minecraft.stack_frame[0].area.endZ int 1 run scoreboard players operation area_endZ mcfpp_default = mcfpp.minecraft_func_build_ez mcfpp_default
#expression end: area.endZ=ez
