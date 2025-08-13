#math.list:sort/c1loop
data modify storage math:io rec[1].result append from storage math:io rec[0].result[0]
data remove storage math:io rec[0].result[0]
scoreboard players remove stemp1 int 1
execute if score stemp1 int matches 1.. run function math.list:sort/c1loop