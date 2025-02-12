#math:hpo/float/test/sqrt

scoreboard players operation test int = rand_seed int
#scoreboard players operation rand_seed int = test int

tellraw @a "---float_sqrt_test---"
function math:hpo/float/_ssrandnew
execute if score float_sign int matches ..-1 run scoreboard players set float_sign int 1
function math:hpo/float/_ssprint
function math:hpo/float/_sqrt
function math:hpo/float/_ssprint