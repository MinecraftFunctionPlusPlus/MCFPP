#math:sqrt/range4
scoreboard players operation sstemp int = sqrt int

function math:sqrt/_self
execute store result score sstemp1 int run scoreboard players operation sstemp0 int = sqrt int
scoreboard players operation sstemp1 int *= sstemp0 int
execute if score sstemp1 int > sstemp int run function math:sqrt/rmv
scoreboard players operation sstemp2 int = sstemp0 int
scoreboard players operation sstemp2 int *= 2 int
scoreboard players add sstemp2 int 1

scoreboard players operation sstemp0 int *= 1000 int
scoreboard players operation sqrt int = sstemp int
scoreboard players operation sqrt int -= sstemp1 int
scoreboard players operation sstemp3 int = sqrt int
scoreboard players operation sqrt int /= sstemp2 int
scoreboard players operation sstemp3 int %= sstemp2 int
scoreboard players operation sqrt int *= 1000 int
scoreboard players operation sstemp3 int *= 1000 int
scoreboard players operation sstemp3 int /= sstemp2 int
scoreboard players operation sqrt int += sstemp3 int
scoreboard players operation sqrt int += sstemp0 int