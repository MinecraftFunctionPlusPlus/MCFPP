#math.3vec_float:test/new

scoreboard players operation test int = rand_seed int
#scoreboard players operation rand_seed int = test int

tellraw @a "---3vec_float_new_test---"

kill @e[tag=test]
summon marker 0 11 0 {Tags:["vec0","test"]}
summon marker 0 11 0 {Tags:["vec1","test"]}
summon marker 0 11 0 {Tags:["vec2","test"]}

execute as @e[tag=math_marker,limit=1] run function math.3vec_float:_ssrandnew
function math.3vec_float:_ssprint
execute as @e[tag=vec0,limit=1] run function math.3vec_float:_store

scoreboard players set inp int 20000
function math.3vec_float:hpo/float/_scoreto
function math.3vec_float:hpo/float/_store
function math.3vec_float:_mult
function math.3vec_float:_ssprint
execute as @e[tag=vec1,limit=1] run function math.3vec_float:_store

scoreboard players set inp int 40000
function math.3vec_float:hpo/float/_scoreto
function math.3vec_float:hpo/float/_store
function math.3vec_float:_div
function math.3vec_float:_ssprint
execute as @e[tag=vec2,limit=1] run function math.3vec_float:_store