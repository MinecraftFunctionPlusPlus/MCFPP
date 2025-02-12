#math:test/run
kill @e[tag=test]
summon item_display ~ ~ ~ {item:{id:"minecraft:grass",Count:1b},Tags:["test"]}
data merge entity @e[tag=test,limit=1] {transformation:{scale:[5.25f,5.25f,5.25f],translation:[-2.0f,0.0f,0.0f]},start_interpolation:0,interpolation_duration:20}

scoreboard players set test int 0