#math:sqrt/range3
scoreboard players operation sstemp int = inp int

scoreboard players operation inp int *= 10000 int
function math:_sqrt
execute store result score sstemp1 int run scoreboard players operation sstemp0 int = res int
scoreboard players operation sstemp1 int *= sstemp0 int
execute if score sstemp1 int > inp int run function math:sqrt/rmv
scoreboard players operation sstemp2 int = sstemp0 int
scoreboard players operation sstemp2 int *= 2 int
scoreboard players add sstemp2 int 1

scoreboard players operation sstemp0 int *= 10 int
scoreboard players operation res int = inp int
scoreboard players operation res int -= sstemp1 int
scoreboard players operation sstemp3 int = res int
scoreboard players operation res int /= sstemp2 int
scoreboard players operation sstemp3 int %= sstemp2 int
scoreboard players operation res int *= 10 int
scoreboard players operation sstemp3 int *= 10 int
scoreboard players operation sstemp3 int /= sstemp2 int
scoreboard players operation res int += sstemp3 int
scoreboard players operation res int += sstemp0 int

scoreboard players operation inp int = sstemp int