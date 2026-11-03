#math.list:sort/loop0
data modify storage math:io rec[0].input append from storage math:io rec[1].input[0]
data remove storage math:io rec[1].input[0]
scoreboard players remove stemp int 1
execute if score stemp int matches 1.. run function math.list:sort/loop0