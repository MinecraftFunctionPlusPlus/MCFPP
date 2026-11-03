#math.list:sort/mloop0
data modify storage math:io cmp.a set from storage math:io rec[0].temp[0]
data modify storage math:io cmp.b set from storage math:io rec[0].result[0]
function math.list:sort/_cmp
execute if score sres int matches 1 run function math.list:sort/mchoice1
execute if score sres int matches 0 run function math.list:sort/mchoice0
execute if score stemp0 int matches 1.. if score stemp1 int matches 1.. run function math.list:sort/mloop0