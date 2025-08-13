#math.list:test/toset

tellraw @a "---list_test_toset---"

scoreboard players set list_phi int 0
data modify storage math:io list set value []

scoreboard players set inp int 2
data modify storage math:io input set value "hello"
function math.list:_fill

scoreboard players set inp int 1
data modify storage math:io input set value "world"
function math.list:_fill

scoreboard players set inp int 3
data modify storage math:io input set value "cber"
function math.list:_fill

tellraw @a ["list: ",{"nbt":"list","storage":"math:io"}]

execute as @e[tag=math_marker,limit=1] run function math.list:_toset

tellraw @a ["set: ",{"nbt":"set","storage":"math:io"}]
tellraw @a ["set_tags: ",{"nbt":"set_tags","storage":"math:io"}]
tellraw @a ["set_cnt: ",{"score":{"name":"set_cnt","objective":"int"}}]