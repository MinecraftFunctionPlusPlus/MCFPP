#math.float:hpo/float/test/inverse

scoreboard players operation test int = rand_seed int
#scoreboard players operation rand_seed int = test int

tellraw @a "---float_inverse_test---"
function math.float:hpo/float/_ssrandnew
function math.float:hpo/float/_ssprint
function math.float:hpo/float/_inverse
function math.float:hpo/float/_ssprint
function math.float:hpo/float/_inverse
function math.float:hpo/float/_ssprint