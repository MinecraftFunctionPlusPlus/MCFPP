#math:set/test/ex-tags

tellraw @a "---set_test_ex-tags---"

function math:set/_ssnew
data modify storage math:io set set value ["hi","test"]
execute store result score set_cnt int if data storage math:io set[]
function math:set/_ex-tags

data modify storage math:io input set value {tags:["test","set"]}
function math:set/_new
execute as @e[tag=set,limit=1] run function math:set/_store
execute as @e[tag=set,limit=1] run function math:set/_print

kill @e[tag=test]