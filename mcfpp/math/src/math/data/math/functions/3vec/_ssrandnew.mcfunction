#math:3vec/_ssrandnew
scoreboard players set min int -30000
scoreboard players set max int 30000
function math:_random
scoreboard players operation 3vec_x int = random int
function math:_random
scoreboard players operation 3vec_y int = random int
function math:_random
scoreboard players operation 3vec_z int = random int