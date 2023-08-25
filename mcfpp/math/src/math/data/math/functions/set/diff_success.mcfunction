#math:set/diff_success
data modify storage math:io set append from storage math:io stemp[-1]
data modify storage math:io set_tags append from storage math:io stemp_tags[-1]
scoreboard players add set_cnt int 1
scoreboard players operation sstemp0 int = sstemp1 int