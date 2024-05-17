package top.mcfpp.jni;

import java.io.File;

public class Test {
    static {
        File file = new File("build/dll/native.dll");
        System.load(file.getAbsolutePath());
    }

    public native void nativeMethod();
}
