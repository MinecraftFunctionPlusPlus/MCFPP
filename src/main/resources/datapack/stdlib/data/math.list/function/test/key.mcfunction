#math.list:test/key

tellraw @a "---list_test_key---"
scoreboard players set inp int 12

function math:randstring/_pull

scoreboard players set list_phi int 0
data modify storage math:io list set value []
data modify storage math:io stemp set value {key:""}
scoreboard players operation loop int = inp int
execute if score loop int matches 1.. run function math.list:test/key_loop
tellraw @a ["list: ",{"nbt":"list","storage":"math:io"}]

data modify storage math:io input set from storage math:io list[8].key
function math.list:_key
tellraw @a ["res: ",{"score":{"name":"res","objective":"int"}}]
tellraw @a ["list: ",{"nbt":"list","storage":"math:io"}]

data modify storage math:io input set value "哈"
function math.list:_key
tellraw @a ["res: ",{"score":{"name":"res","objective":"int"}}]

function math:randstring/_back