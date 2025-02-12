#math:test/perf/_tick

worldborder set 1 0
worldborder set 50000000 50000
scoreboard players operation loop int = perf_n int
execute if score loop int matches 1.. run function math:test/perf/_loop
execute store result score res int run worldborder get
scoreboard players operation perf_avg int += res int
scoreboard players operation perf_avg int /= 2 int
scoreboard players operation perf_min int < res int
scoreboard players operation perf_max int > res int

worldborder set 50000000 0