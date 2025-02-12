#math:hpo/float/test/add

scoreboard players operation test int = rand_seed int
#scoreboard players operation rand_seed int = test int

tellraw @a "---float_add_test---"
function math:hpo/float/_ssrandnew
function math:hpo/float/_ssprint
function math:hpo/float/_store

function math:hpo/float/_ssrandnew
scoreboard players set min int -7
scoreboard players set max int 7
function math:_random
scoreboard players operation float_exp int = @s float_exp
scoreboard players operation float_exp int += random int
function math:hpo/float/_ssprint

function math:hpo/float/_add
function math:hpo/float/_ssprint