#include <jni.h>
#include <algorithm>
#include <vector>
#include "AudioEngine.h"
static AudioEngine g;
static std::vector<float> arr(JNIEnv*e,jfloatArray a){if(!a)return{};jsize n=e->GetArrayLength(a);std::vector<float>v(n);e->GetFloatArrayRegion(a,0,n,v.data());return v;}
extern "C" JNIEXPORT jboolean JNICALL Java_com_sonolume_spectra_NativeAudio_start(JNIEnv*,jobject){return g.start();}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_stop(JNIEnv*,jobject){g.stop();}
extern "C" JNIEXPORT jboolean JNICALL Java_com_sonolume_spectra_NativeAudio_isRunning(JNIEnv*,jobject){return g.running();}
extern "C" JNIEXPORT jint JNICALL Java_com_sonolume_spectra_NativeAudio_sampleRate(JNIEnv*,jobject){return g.sampleRate();}
extern "C" JNIEXPORT jint JNICALL Java_com_sonolume_spectra_NativeAudio_xRunCount(JNIEnv*,jobject){return g.xRunCount();}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_setMaster(JNIEnv*,jobject,jfloat v){g.core().setMaster(v);}extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_setTrack(JNIEnv*,jobject,jint t,jfloat v){g.core().setTrack(t,v);}extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_setGlobalReverb(JNIEnv*,jobject,jfloat v){g.core().setGlobalReverb(v);}extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_setGlobalDelayMix(JNIEnv*,jobject,jfloat v){g.core().setGlobalDelayMix(v);}extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_setGlobalDelayTime(JNIEnv*,jobject,jfloat v){g.core().setGlobalDelayTime(v);}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_setNebFx(JNIEnv*,jobject,jboolean a,jfloat b,jfloat c,jfloat d,jboolean e,jfloat f,jfloat h,jfloat i,jboolean j,jfloat k,jfloat l){g.core().setNebFx(a,b,c,d,e,f,h,i,j,k,l);}extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_setSpecFx(JNIEnv*,jobject,jboolean a,jfloat b,jfloat c,jfloat d,jboolean e,jfloat f,jfloat h){g.core().setSpecFx(a,b,c,d,e,f,h);}extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_setDrumFx(JNIEnv*,jobject,jboolean a,jfloat b){g.core().setDrumFx(a,b);}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_nebNote(JNIEnv*env,jobject,jint midi,jfloat vel,jfloat dur,jfloat delay,jboolean pluck,jfloat decay,jfloatArray bt,jfloatArray bv,jfloatArray vt,jfloatArray vv){auto a=arr(env,bt),b=arr(env,bv),c=arr(env,vt),d=arr(env,vv);int bn=std::min(a.size(),b.size()),vn=std::min(c.size(),d.size());g.core().nebNote(midi,vel,dur,delay,pluck,decay,a.data(),b.data(),bn,c.data(),d.data(),vn);}extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_specNote(JNIEnv*,jobject,jint midi,jfloat vel,jfloat dur,jfloat delay,jint wave){g.core().specNote(midi,vel,dur,delay,wave);}extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_drumHit(JNIEnv*,jobject,jint kind,jfloat freq,jfloat vel,jfloat pos,jfloat delay){g.core().drumHit(kind,freq,vel,pos,delay);}extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_click(JNIEnv*,jobject,jboolean hi,jfloat delay){g.core().click(hi,delay);}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_nebLiveStart(JNIEnv*,jobject,jint id,jint midi,jfloat level,jfloat pressure,jfloat muteMix,jfloat cents,jfloat volume,jboolean pluck,jfloat releaseMs){g.core().nebLiveStart(id,midi,level,pressure,muteMix,cents,volume,pluck,releaseMs);}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_nebLiveUpdate(JNIEnv*,jobject,jint id,jint midi,jfloat level,jfloat pressure,jfloat muteMix,jfloat cents,jfloat volume){g.core().nebLiveUpdate(id,midi,level,pressure,muteMix,cents,volume);}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_nebLiveStop(JNIEnv*,jobject,jint id,jfloat releaseMs){g.core().nebLiveStop(id,releaseMs);}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_specLiveStart(JNIEnv*,jobject,jint id,jint midi,jfloat gain,jint wave){g.core().specLiveStart(id,midi,gain,wave);}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_specLiveStop(JNIEnv*,jobject,jint id){g.core().specLiveStop(id);}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_livePanic(JNIEnv*,jobject){g.core().livePanic();}

static std::vector<int> ints(JNIEnv*e,jintArray a){if(!a)return{};jsize n=e->GetArrayLength(a);std::vector<int>v(n);e->GetIntArrayRegion(a,0,n,reinterpret_cast<jint*>(v.data()));return v;}
static std::vector<float> floats(JNIEnv*e,jfloatArray a){if(!a)return{};jsize n=e->GetArrayLength(a);std::vector<float>v(n);e->GetFloatArrayRegion(a,0,n,v.data());return v;}
static std::vector<unsigned char> bools(JNIEnv*e,jbooleanArray a){if(!a)return{};jsize n=e->GetArrayLength(a);std::vector<unsigned char>v(n);e->GetBooleanArrayRegion(a,0,n,reinterpret_cast<jboolean*>(v.data()));return v;}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_seqConfigure(JNIEnv*,jobject,jfloat bpm,jint top,jint bottom,jboolean loop,jboolean metro){g.core().seqConfigure(bpm,top,bottom,loop,metro);}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_seqSetAudible(JNIEnv*,jobject,jboolean n,jboolean s,jboolean d){g.core().seqSetAudible(n,s,d);}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_seqSetNebDecay(JNIEnv*,jobject,jfloat v){g.core().seqSetNebDecay(v);}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_seqSetSpecWaves(JNIEnv*,jobject,jint a,jint b){g.core().seqSetSpecWaves(a,b);}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_seqSetNeb(JNIEnv*e,jobject,jintArray s,jintArray midi,jintArray len,jfloatArray vel,jbooleanArray pluck,jintArray bendN,jfloatArray bendT,jfloatArray bendV,jintArray vexN,jfloatArray vexT,jfloatArray vexV){
    auto a=ints(e,s);
    auto b=ints(e,midi);
    auto cc=ints(e,len);
    auto f=floats(e,vel);
    auto h=bools(e,pluck);
    auto bn=ints(e,bendN);
    auto bt=floats(e,bendT);
    auto bv=floats(e,bendV);
    auto vn=ints(e,vexN);
    auto vt=floats(e,vexT);
    auto vv=floats(e,vexV);
    int n=a.size();
    g.core().seqSetNeb(n,a.data(),b.data(),cc.data(),f.data(),h.data(),bn.data(),bt.data(),bv.data(),vn.data(),vt.data(),vv.data());
}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_seqSetSpec(JNIEnv*e,jobject,jintArray s,jintArray m1,jintArray m2,jintArray len,jfloatArray vel){
    auto a=ints(e,s);
    auto b=ints(e,m1);
    auto d=ints(e,m2);
    auto cc=ints(e,len);
    auto f=floats(e,vel);
    int n=a.size();
    g.core().seqSetSpec(n,a.data(),b.data(),d.data(),cc.data(),f.data());
}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_seqSetDrums(JNIEnv*e,jobject,jintArray s,jintArray midi,jfloatArray vel){
    auto a=ints(e,s);
    auto b=ints(e,midi);
    auto f=floats(e,vel);
    int n=a.size();
    g.core().seqSetDrums(n,a.data(),b.data(),f.data());
}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_seqPlay(JNIEnv*,jobject){g.core().seqPlay();}
extern "C" JNIEXPORT void JNICALL Java_com_sonolume_spectra_NativeAudio_seqStop(JNIEnv*,jobject){g.core().seqStop();}
extern "C" JNIEXPORT jboolean JNICALL Java_com_sonolume_spectra_NativeAudio_seqIsPlaying(JNIEnv*,jobject){return g.core().seqIsPlaying();}
extern "C" JNIEXPORT jint JNICALL Java_com_sonolume_spectra_NativeAudio_seqStep(JNIEnv*,jobject){return g.core().seqStep();}
