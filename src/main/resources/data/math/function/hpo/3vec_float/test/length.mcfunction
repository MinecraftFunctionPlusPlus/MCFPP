#math:hpo/3vec_float/test/length

scoreboard players operation test int = rand_seed int
#scoreboard players operation rand_seed int = test int

tellraw @a "---3vec_float_length_test---"
execute as @e[tag=math_marker,limit=1] run function math:hpo/3vec_float/_ssrandnew
function math:hpo/3vec_float/_unit
function math:hpo/3vec_float/_length
function math:hpo/float/_ssprint