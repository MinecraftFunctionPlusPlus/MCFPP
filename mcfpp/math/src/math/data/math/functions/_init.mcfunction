#math:_init
# 初始化数学库
# 使用数学库之前必须手动执行

#主世界设置
execute in minecraft:the_overworld run function math:init_overworld

#全局记分板
scoreboard objectives add id dummy
scoreboard objectives add int dummy

#常量设置
scoreboard players set -10000 int -10000
scoreboard players set -1000 int -1000
scoreboard players set -2 int -2
scoreboard players set -1 int -1
scoreboard players set 0 int 0
scoreboard players set 1 int 1
scoreboard players set 2 int 2
scoreboard players set 3 int 3
scoreboard players set 4 int 4
scoreboard players set 5 int 5
scoreboard players set 6 int 6
scoreboard players set 7 int 7
scoreboard players set 8 int 8
scoreboard players set 9 int 9
scoreboard players set 10 int 10
scoreboard players set 16 int 16
scoreboard players set 25 int 25
scoreboard players set 40 int 40
scoreboard players set 50 int 50
scoreboard players set 100 int 100
scoreboard players set 119 int 119
scoreboard players set 500 int 500
scoreboard players set 1000 int 1000
scoreboard players set 1745 int 1745
scoreboard players set 2550 int 2550
scoreboard players set 4214 int 4214
scoreboard players set 5000 int 5000
scoreboard players set 5100 int 5100
scoreboard players set 5202 int 5202
scoreboard players set 10000 int 10000
scoreboard players set 12500 int 12500
scoreboard players set 50000 int 50000
scoreboard players set 50436 int 50436
scoreboard players set 100000 int 100000
scoreboard players set 1000000 int 1000000
scoreboard players set 3600000 int 3600000
scoreboard players set 10000000 int 10000000
scoreboard players set 100000000 int 100000000
scoreboard players set 1000000000 int 1000000000
scoreboard players set 1103515245 int 1103515245

#高频循环
schedule function math:tick 1t replace

#数据模板
data modify storage math:class cmp_rules set value {"a<b":0b,"a>b":1b,"a<=b":0b,"a>=b":1b}
data modify storage math:class chars set value ["0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z"]
data modify storage math:class items set value [{id:"minecraft:red_wool",Count:1b,name:"r"},{id:"minecraft:green_wool",Count:1b,name:"g"},{id:"minecraft:blue_wool",Count:1b,name:"b"}]

#临时对象初始化
data modify storage math:io item set value {}

data modify storage math:io classify set value []
scoreboard players set classify_cnt int 0

data modify storage math:io rand_lst set value [8593,45316,3528,4112,43,296358,12936510]
execute store result score rand_seed int run data get storage math:io rand_lst[0]

scoreboard players set particle int 4
scoreboard players set 3vec_n int 200

#递归函数栈
data modify storage math:io rec set value [{}]

#三维向量初始化
function math:3vec/init

#列表初始化
function math:list/init

#四元数初始化
function math:qrot/init

#局部坐标系初始化
function math:uvw/init

#空间模块初始化
function math:space/init

#实体碰撞箱初始化
function math:cbox/init

#几何图形初始化
function math:geom/init

#高精度模块初始化
function math:hpo/init

#集合初始化
function math:set/init