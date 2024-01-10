#include <jni.h>
#include <iostream>

#include "top_mcfpp_jni_Test.h"

JNIEXPORT void JNICALL Java_top_mcfpp_jni_Test_nativeMethod(JNIEnv *, jobject){
    std::cout << "Hello JNI" << std::endl;
}