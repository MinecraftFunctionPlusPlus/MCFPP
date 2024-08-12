#math:set/test/diff

tellraw @a "---set_test_diff---"

data modify storage math:io input set value {tags:["test","set"]}
function math:set/_new
data modify storage math:io list set value ["hi","this","is"]
function math:list/_toset
execute as @e[tag=set,limit=1] run function math:set/_store

data modify storage math:io list set value ["hi","minecraft","is","me"]
function math:list/_toset
execute as @e[tag=set,limit=1] run function math:set/_diff
function math:set/_ssprint

data modify storage math:io list set value ["hi","this","is","minecraft"]
function math:list/_toset
execute as @e[tag=set,limit=1] run function math:set/_diff
function math:set/_ssprint

data modify storage math:io list set value ["no","minecraft"]
function math:list/_toset
execute as @e[tag=set,limit=1] run function math:set/_diff
function math:set/_ssprint

kill @e[tag=test]