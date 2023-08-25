#math:hpo/3vec_float/test/main
#传入世界实体为执行者

scoreboard players set 3vec_n int 50

execute as @e[tag=vec0,limit=1] run function math:hpo/3vec_float/_get
function math:hpo/3vec_float/_tovec
scoreboard players set particle int 9
function math:3vec/_dsp-ex

execute as @e[tag=vec1,limit=1] run function math:hpo/3vec_float/_get
function math:hpo/3vec_float/_tovec
scoreboard players set particle int 10
function math:3vec/_dsp-ex

execute as @e[tag=vec2,limit=1] run function math:hpo/3vec_float/_get
function math:hpo/3vec_float/_tovec
scoreboard players set particle int 11
function math:3vec/_dsp-ex