#pragma once
#include <oboe/Oboe.h>
#include <memory>
#include <mutex>
#include "DspCore.h"

class AudioEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    bool start();
    void stop();
    bool running() const { return mRunning.load(); }
    int sampleRate() const { return mCore.sampleRate(); }
    int xRunCount() const;
    DspCore& core(){ return mCore; }
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;
private:
    mutable std::mutex mMutex;
    std::shared_ptr<oboe::AudioStream> mStream;
    DspCore mCore;
    std::atomic<bool> mRunning{false};
};
