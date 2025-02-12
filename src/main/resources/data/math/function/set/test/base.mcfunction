#math:set/test/base

tellraw @a "---set_test_base---"
data modify storage math:io list set value ["hello","hello","world","im","a","a","cber"]
function math:list/_toset
data modify storage math:io input set value {tags:["A","test"]}
function math:set/_new
execute as @e[tag=result,limit=1] run function math:set/_store

data modify storage math:io list set value ["hi","hi","cber","this","is","is","minecraft"]
function math:list/_toset
data modify storage math:io input set value {tags:["B","test"]}
function math:set/_new
execute as @e[tag=result,limit=1] run function math:set/_store

execute as @e[tag=A,limit=1] run function math:set/_print
execute as @e[tag=B,limit=1] run function math:set/_print

execute as @e[tag=A,limit=1] run function math:set/_get
data modify storage math:io input set value "8593"
function math:set/_append
execute as @e[tag=B,limit=1] run function math:set/_store
execute as @e[tag=B,limit=1] run function math:set/_print

function math:set/_ssnew
data modify storage math:io input set value "8593"
function math:set/_append
data modify storage math:io input set value "3528"
function math:set/_append
execute as @e[tag=B,limit=1] run function math:set/_store
execute as @e[tag=B,limit=1] run function math:set/_print

data modify storage math:io list set value ["no","name"]
function math:list/_toset
function math:set/_ssprint
execute as @e[tag=B,limit=1] run function math:set/_swap
execute as @e[tag=B,limit=1] run function math:set/_print
function math:set/_ssprint

kill @e[tag=test]