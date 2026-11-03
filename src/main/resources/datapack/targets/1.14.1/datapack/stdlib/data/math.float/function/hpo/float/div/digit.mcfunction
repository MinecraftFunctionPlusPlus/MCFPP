# remainder < denominator; multiplying by ten stays below one billion.
scoreboard players operation div_remainder int *= 10 int
scoreboard players operation div_digit int = div_remainder int
scoreboard players operation div_digit int /= div_denominator int
scoreboard players operation div_accumulator int *= 10 int
scoreboard players operation div_accumulator int += div_digit int
scoreboard players operation div_remainder int %= div_denominator int
