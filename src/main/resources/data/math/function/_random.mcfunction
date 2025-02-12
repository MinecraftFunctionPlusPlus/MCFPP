#math:_random
# 进行一次随机数迭代
# 输入随机数范围{<min,int>,<max,int>}
# 输出<random,int>
scoreboard players operation sstemp int = max int
scoreboard players operation sstemp int -= min int
scoreboard players add sstemp int 1
scoreboard players operation rand_seed int *= 1103515245 int
scoreboard players add rand_seed int 12345
scoreboard players operation random int = rand_seed int
scoreboard players operation random int %= sstemp int
scoreboard players operation random int += min int