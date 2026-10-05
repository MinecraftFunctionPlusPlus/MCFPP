# Positive magnitudes: initial integer digit, followed by seven or eight decimal digits.
scoreboard players operation div_remainder int = float_int0 int
scoreboard players operation div_remainder int *= 10000 int
scoreboard players operation div_remainder int += float_int1 int
scoreboard players operation div_denominator int = @s float_int0
scoreboard players operation div_denominator int *= 10000 int
scoreboard players operation div_denominator int += @s float_int1
scoreboard players operation div_accumulator int = div_remainder int
scoreboard players operation div_accumulator int /= div_denominator int
scoreboard players operation div_remainder int %= div_denominator int
scoreboard players set div_fractional int 1
execute if score div_accumulator int matches 1.. run scoreboard players set div_fractional int 0
scoreboard players operation float_exp int -= @s float_exp
execute if score div_fractional int matches 0 run scoreboard players add float_exp int 1
function math.float:hpo/float/div/digit
function math.float:hpo/float/div/digit
function math.float:hpo/float/div/digit
function math.float:hpo/float/div/digit
function math.float:hpo/float/div/digit
function math.float:hpo/float/div/digit
function math.float:hpo/float/div/digit
execute if score div_fractional int matches 1 run function math.float:hpo/float/div/digit
scoreboard players operation float_int0 int = div_accumulator int
scoreboard players operation float_int1 int = div_accumulator int
scoreboard players operation float_int0 int /= 10000 int
scoreboard players operation float_int1 int %= 10000 int
scoreboard players operation float_sign int *= @s float_sign
