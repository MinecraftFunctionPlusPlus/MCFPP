package top.mcfpp.jni;

import java.io.File;
import java.net.URL;

public class Test {
    static {
        File file = new File("build/dll/native.dll");
        System.load(file.getAbsolutePath());
    }

    public native void nativeMethod();
}
