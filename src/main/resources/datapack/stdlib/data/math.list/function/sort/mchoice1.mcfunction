#math.list:sort/mchoice1
data modify storage math:io rec[1].result append from storage math:io rec[0].temp[0]
data remove storage math:io rec[0].temp[0]
scoreboard players remove stemp0 int 1