package top.mcfpp.test;

public class Test2 {
    public static void main(String[] args) {

        double a = 1.0;
        double b = 2.0;

        double result = average(a, b);
        System.out.println(result);
    }

    static double average(double a, double b){
        double a1 = 1/a;
        double b1 = 1/b;
        double av = (a1 + b1)/2;
        return 1/av;
    }
}
