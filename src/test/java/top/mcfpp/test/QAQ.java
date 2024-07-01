package top.mcfpp.test;

import java.util.Arrays;

public class QAQ {
    public static void main(String[] args) {
        System.out.println(findTargetSumWays(new int[]{1,0},1));
    }

    public static int findTargetSumWays(int[] nums, int target) {
        //求和
        int sum = 0;
        for (int num : nums) {
            sum += num;
        }
        //如果sum小于target或者sum+target为奇数，直接返回0
        if (sum < target || (sum - target) % 2 == 1) {
            return 0;
        }
        //求差
        target = (sum - target) / 2;
        //从小往大找
        return solve(nums, target);
    }

    public static int solve(int[] nums, int target){
        //从小往大找
        int count = 0;
        if(target == 0) count++;
        for (int i = 0; i < nums.length; i++) {
            count += solve(Arrays.copyOfRange(nums, i + 1, nums.length), target - nums[i]);
        }
        return count;
    }
}
