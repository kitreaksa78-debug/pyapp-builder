#include <jni.h>
#include <cmath>

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_poolmod_GuidelineView_calculateRaycast(
        JNIEnv* env,
        jobject obj,
        jfloat startX, jfloat startY,
        jfloat targetX, jfloat targetY,
        jfloat length) {

    float dx = targetX - startX;
    float dy = targetY - startY;
    float distance = std::sqrt(dx * dx + dy * dy);

    float endX = startX + (dx / distance) * length;
    float endY = startY + (dy / distance) * length;

    jfloatArray result = env->NewFloatArray(4);
    jfloat resultBuffer[4] = {startX, startY, endX, endY};
    env->SetFloatArrayRegion(result, 0, 4, resultBuffer);

    return result;
}
