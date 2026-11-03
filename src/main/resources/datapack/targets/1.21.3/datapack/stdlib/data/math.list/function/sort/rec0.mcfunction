#math.list:sort/rec0
data modify storage math:io rec prepend value {}

execute store result score stemp int if data storage math:io rec[1].input[]
execute if score stemp int matches ..1 run data modify storage math:io rec[1].result set from storage math:io rec[1].input
execute if score stemp int matches 2.. run function math.list:sort/merge0

data remove storage math:io rec[0]