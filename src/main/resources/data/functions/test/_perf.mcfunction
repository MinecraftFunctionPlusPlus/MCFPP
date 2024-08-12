#math:test/_perf
worldborder set 1 0

worldborder set 50000000 50000
scoreboard players operation loop int = perf_n int
execute if score loop int matches 1.. run function math:test/perf/_loop

execute store result score res int run worldborder get
tellraw @a ["res: ",{"score":{"name":"res","objective":"int"}}]
worldborder set 50000000 0