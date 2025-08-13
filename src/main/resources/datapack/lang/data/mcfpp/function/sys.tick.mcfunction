execute as @e[type=marker,tag=mcfpp_gc] if score @s mcfpp_pointer_counter matches ..0 run kill @s
execute if data storage mcfpp:system stack_frame[0] run tellraw @a {"text":"[MCFPP]Stack Leak"}
execute if data storage mcfpp:system stack_frame[0] run data modify storage mcfpp:system stack_frame set value []
