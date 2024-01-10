package top.mcfpp.test;

import top.mcfpp.jni.Test;

public class NativeTest {
    public static void main(String[] args) {
        Test test = new Test();
        test.nativeMethod();
    }
}
