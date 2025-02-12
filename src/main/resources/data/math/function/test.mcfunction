#math:test
summon item 0 11 0 {Tags:["uuid_marker"],Item:{id:"minecraft:glass",Count:1b},CustomName:'"origin_test"'}
execute as @e[tag=uuid_marker,limit=1] run function math:test/origin
kill @e[tag=uuid_marker,limit=1]