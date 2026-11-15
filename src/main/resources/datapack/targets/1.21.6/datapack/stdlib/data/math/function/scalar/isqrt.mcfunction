scoreboard players operation ms_mid int = ms_low int
scoreboard players operation ms_mid int += ms_high int
scoreboard players operation ms_mid int /= 2 int
scoreboard players operation ms_quotient int = ms_input int
scoreboard players operation ms_quotient int /= ms_mid int
execute if score ms_mid int <= ms_quotient int run scoreboard players operation ms_low int = ms_mid int
execute if score ms_mid int > ms_quotient int run scoreboard players operation ms_high int = ms_mid int
scoreboard players operation ms_gap int = ms_high int
scoreboard players operation ms_gap int -= ms_low int
execute if score ms_gap int matches 2.. run function math:scalar/isqrt
