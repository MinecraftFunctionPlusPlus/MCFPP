tag @e[tag=stmp0,limit=1] remove stmp0
scoreboard players remove stemp0 int 1
execute if score stemp0 int > stemp1 int run function math.list:shuffle/remove_loop