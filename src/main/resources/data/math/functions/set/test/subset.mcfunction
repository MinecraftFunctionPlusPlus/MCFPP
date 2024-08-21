#math:set/test/subset

tellraw @a "---set_test_subset---"

data modify storage math:io input set value {tags:["test","set","A"]}
function math:set/_new
data modify storage math:io input set value {tags:["test","set","B"]}
function math:set/_new

data modify storage math:io list set value ["hi","hello","there","this","minecraft"]
function math:list/_toset
execute as @e[tag=A,limit=1] run function math:set/_store

data modify storage math:io list set value ["hi","there","this"]
function math:list/_toset
execute as @e[tag=B,limit=1] run function math:set/_store
execute as @e[tag=A,limit=1] run function math:set/_get
execute as @e[tag=B,limit=1] run function math:set/_subset
tellraw @a ["res: ",{"score":{"name":"res","objective":"int"}}]

data modify storage math:io list set value ["hi","there","this","no"]
function math:list/_toset
execute as @e[tag=B,limit=1] run function math:set/_store
execute as @e[tag=A,limit=1] run function math:set/_get
execute as @e[tag=B,limit=1] run function math:set/_subset
tellraw @a ["res: ",{"score":{"name":"res","objective":"int"}}]

kill @e[tag=test]