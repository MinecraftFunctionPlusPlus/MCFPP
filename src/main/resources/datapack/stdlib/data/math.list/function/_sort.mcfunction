#math.list:_sort
data modify storage math:io cmp set value {a:1b,b:1b}
execute store result score stemp_cmp int run data get storage math:io sort_cmp
data modify storage math:io rec prepend value {}

data modify storage math:io rec[0].input set from storage math:io list
function math.list:sort/rec0
data modify storage math:io list set from storage math:io rec[0].result

data remove storage math:io rec[0]