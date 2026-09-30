#include "AudioEngine.h"
#include <android/log.h>
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,"SpectraNative",__VA_ARGS__)

bool AudioEngine::start(){
    std::lock_guard<std::mutex> lock(mMutex); if(mRunning.load()&&mStream) return true;
    oboe::AudioStreamBuilder b; b.setDirection(oboe::Direction::Output); b.setPerformanceMode(oboe::PerformanceMode::LowLatency); b.setSharingMode(oboe::SharingMode::Exclusive); b.setFormat(oboe::AudioFormat::Float); b.setChannelCount(2); b.setUsage(oboe::Usage::Game); b.setContentType(oboe::ContentType::Music); b.setDataCallback(this); b.setErrorCallback(this);
    auto r=b.openStream(mStream); if(r!=oboe::Result::OK||!mStream){LOGE("openStream failed: %s",oboe::convertToText(r));mStream.reset();return false;}
    mCore.prepare(mStream->getSampleRate());
    if(mStream->getFramesPerBurst()>0) mStream->setBufferSizeInFrames(mStream->getFramesPerBurst()*3);
    r=mStream->requestStart(); if(r!=oboe::Result::OK){LOGE("requestStart failed: %s",oboe::convertToText(r));mStream->close();mStream.reset();return false;}
    mRunning.store(true);return true;
}
void AudioEngine::stop(){std::lock_guard<std::mutex> lock(mMutex);mRunning.store(false);if(mStream){mStream->requestStop();mStream->close();mStream.reset();}mCore.reset();}
int AudioEngine::xRunCount() const{std::lock_guard<std::mutex> lock(mMutex);if(!mStream)return 0;auto r=mStream->getXRunCount();return r? r.value():0;}
oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream*,void*data,int32_t frames){if(!mRunning.load()){std::fill_n(static_cast<float*>(data),frames*2,0.f);return oboe::DataCallbackResult::Continue;}mCore.render(static_cast<float*>(data),frames);return oboe::DataCallbackResult::Continue;}
void AudioEngine::onErrorAfterClose(oboe::AudioStream*,oboe::Result error){LOGE("stream error: %s",oboe::convertToText(error));mRunning.store(false);}
