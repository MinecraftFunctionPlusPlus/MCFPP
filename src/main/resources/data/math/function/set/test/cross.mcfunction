#math:set/test/cross

data modify storage math:io input set value {tags:["test","set"]}
function math:set/_new
data modify storage math:io list set value ["hi","there","cber","name","minecraft"]
function math:list/_toset
execute as @e[tag=set,limit=1] run function math:set/_store

data modify storage math:io list set value ["hi","hello","there","this"]
function math:list/_toset
execute as @e[tag=set,limit=1] run function math:set/_cross
function math:set/_ssprint

data modify storage math:io list set value ["no","he","does"]
function math:list/_toset
execute as @e[tag=set,limit=1] run function math:set/_cross
function math:set/_ssprint

data modify storage math:io list set value ["cber","is","name"]
function math:list/_toset
execute as @e[tag=set,limit=1] run function math:set/_cross
function math:set/_ssprint

kill @e[tag=test]