#math.list:test/fill

tellraw @a "---list_test_fill---"
scoreboard players set list_phi int 0
data modify storage math:io list set value []

data modify storage math:io input set value {key:"hi"}
scoreboard players set inp int 5
function math.list:_fill
tellraw @a ["list: ",{"nbt":"list","storage":"math:io"}]