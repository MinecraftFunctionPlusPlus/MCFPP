#math.list:sort/mchoice0
data modify storage math:io rec[1].result append from storage math:io rec[0].result[0]
data remove storage math:io rec[0].result[0]
scoreboard players remove stemp1 int 1