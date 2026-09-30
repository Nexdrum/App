#pragma once
#include <mutex>
#include <array>
#include <atomic>
#include <cstdint>
#include <vector>

class DspCore {
public:
    DspCore();
    ~DspCore();
    void prepare(double sampleRate);
    void reset();
    void render(float* outInterleaved, int32_t frames);

    void setMaster(float v);
    void setTrack(int track, float v);
    void setGlobalReverb(float v);
    void setGlobalDelayMix(float v);
    void setGlobalDelayTime(float v);

    void setNebFx(bool dlyOn,float dlyAmt,float dlyFb,float dlyTime,
                  bool dstOn,float dstAmt,float dstIn,float dstOut,
                  bool verbOn,float verbAmt,float verbDecay);
    void setSpecFx(bool dlyOn,float dlyAmt,float dlyFb,float dlyTime,
                   bool verbOn,float verbAmt,float verbRoom);
    void setDrumFx(bool verbOn,float verbAmt);

    void nebNote(int midi,float vel,float durSec,float delaySec,bool pluck,float decayMs,
                 const float* bendT,const float* bendV,int bendN,
                 const float* vexT,const float* vexV,int vexN);
    void specNote(int midi,float vel,float durSec,float delaySec,int wave);
    void drumHit(int kind,float freq,float vel,float pos,float delaySec);
    void click(bool high,float delaySec);

    // Live performance voices used by the embedded full instruments.
    void nebLiveStart(int id,int midi,float level,float pressure,float muteMix,float cents,float volume,bool pluck,float releaseMs);
    void nebLiveUpdate(int id,int midi,float level,float pressure,float muteMix,float cents,float volume);
    void nebLiveStop(int id,float releaseMs);
    void specLiveStart(int id,int midi,float gain,int wave);
    void specLiveStop(int id);
    void livePanic();

    int sampleRate() const { return static_cast<int>(mSampleRate); }

    void seqConfigure(float bpm,int top,int bottom,bool loop,bool metro);
    void seqSetAudible(bool neb,bool spec,bool drums);
    void seqSetNebDecay(float decayMs);
    void seqSetSpecWaves(int w1,int w2);
    void seqClear();
    void seqSetNeb(int n,const int* s,const int* midi,const int* len,const float* vel,
                   const unsigned char* pluck,const int* bendN,const float* bendT,const float* bendV,
                   const int* vexN,const float* vexT,const float* vexV);
    void seqSetSpec(int n,const int* s,const int* midi1,const int* midi2,const int* len,const float* vel);
    void seqSetDrums(int n,const int* s,const int* midi,const float* vel);
    void seqPlay();
    void seqStop();
    bool seqIsPlaying() const;
    int seqStep() const;

private:
    static constexpr int MAX_POINTS=16;
    static constexpr int EVENT_Q=2048;
    static constexpr int PENDING_MAX=512;
    static constexpr int NEB_VOICES=40;
    static constexpr int SPEC_VOICES=64;
    static constexpr int DRUM_VOICES=28;
    static constexpr int CLICK_VOICES=8;
    static constexpr int NEB_WAVE_LEVELS=17;
    static constexpr int NEB_WAVE_SIZE=1024;

    enum class EventType:uint8_t { Neb, Spec, Drum, Click, NebLiveStart, NebLiveUpdate, NebLiveStop, SpecLiveStart, SpecLiveStop, LivePanic };
    struct Event {
        EventType type{};
        uint64_t frame=0;
        int i0=0,i1=0;
        float a=0,b=0,c=0,d=0,e=0,f=0;
        uint8_t n1=0,n2=0;
        std::array<float,MAX_POINTS> t1{},v1{},t2{},v2{};
    };

    struct Queue {
        std::array<Event,EVENT_Q> q{};
        std::atomic<uint32_t> head{0},tail{0};
        bool push(const Event& e);
        bool pop(Event& e);
    };

    struct Rng {
        uint32_t s=0x12345678u;
        inline float next(){ s^=s<<13; s^=s>>17; s^=s<<5; return (float(s&0x00ffffff)/8388607.5f)-1.f; }
    };

    struct Biquad {
        float b0=1,b1=0,b2=0,a1=0,a2=0,z1=0,z2=0;
        void clear(){z1=z2=0;}
        float process(float x){ float y=b0*x+z1; z1=b1*x-a1*y+z2; z2=b2*x-a2*y; return y; }
        void lowpass(double sr,float f,float q);
        void bandpass(double sr,float f,float q);
        void peaking(double sr,float f,float q,float gainDb);
    };

    struct Delay {
        std::vector<float> buf;
        int write=0;
        void init(int n){buf.assign(n,0.f);write=0;}
        void clear(){std::fill(buf.begin(),buf.end(),0.f);write=0;}
        float tick(float x,int delaySamples,float fb);
    };

    struct Comb {
        std::vector<float> b; int p=0; float damp=0,store=0;
        void init(int n){b.assign(n,0.f);p=0;store=0;}
        void clear(){std::fill(b.begin(),b.end(),0.f);p=0;store=0;}
        float tick(float x,float feedback,float d);
    };
    struct Allpass {
        std::vector<float> b; int p=0;
        void init(int n){b.assign(n,0.f);p=0;}
        void clear(){std::fill(b.begin(),b.end(),0.f);p=0;}
        float tick(float x,float feedback);
    };
    struct Reverb {
        std::array<Comb,4> l{},r{}; std::array<Allpass,2> al{},ar{};
        void init(double sr);
        void clear();
        void tick(float in,float feedback,float damp,float& L,float& R);
    };

    struct NebVoice {
        bool active=false; uint64_t start=0; uint64_t age=0;
        float baseFreq=440,vel=.95f,dur=.3f,rel=.7f,mute=0,pressure=0,onset=.1f;
        bool pluck=false;
        float phase=0,noiseState=0;
        float waveMix=0,currentWaveMix=0;
        float filterBaseFreq=0,filterPressure=-1,filterMute=-1;
        Biquad body{},smooth{},noiseBand{};
        uint8_t bn=0,vn=0; std::array<float,MAX_POINTS> bt{},bv{},vt{},vv{};

        // Live-performance state. Scheduled DAW voices leave live=false.
        bool live=false,releasing=false;
        int liveId=-1;
        uint64_t releaseFrame=0;
        float targetFreq=440,currentFreq=440,targetAmp=.1f,currentAmp=0,liveVolume=1;

        float interp(const std::array<float,MAX_POINTS>& t,const std::array<float,MAX_POINTS>& v,int n,float x,float def) const;
        float sample(double sr,uint64_t frame,Rng& rng,const float* waveTables);
    };

    struct SpecVoice {
        bool active=false; uint64_t start=0,age=0; float freq=440,vel=.9f,dur=.3f,phase=0; int wave=3;
        bool live=false,releasing=false; int liveId=-1; uint64_t releaseFrame=0; float liveGain=.1f;
        float sample(double sr,uint64_t frame);
    };

    struct DrumVoice {
        bool active=false; uint64_t start=0,age=0; int kind=0; float freq=110,vel=1,pos=.5f;
        std::array<float,8> phase{}; std::array<float,8> jitter{};
        Biquad n1{},n2{},n3{}; Rng rng{};
        float life=1;
        float sample(double sr,uint64_t frame);
    };

    struct ClickVoice { bool active=false; uint64_t start=0,age=0; float phase=0,freq=1400; float sample(double sr,uint64_t frame); };

    double mSampleRate=48000.0;
    std::atomic<uint64_t> mFrame{0};
    struct SeqNeb { int s=0,midi=60,len=1; float vel=.95f; bool pluck=false; uint8_t bn=0,vn=0; float bt[MAX_POINTS]{},bv[MAX_POINTS]{},vt[MAX_POINTS]{},vv[MAX_POINTS]{}; };
    struct SeqSpec { int s=0,midi1=60,midi2=72,len=1; float vel=.9f; };
    struct SeqDrum { int s=0,midi=40; float vel=1.f; };
    // Immutable sequencer content is published atomically to the real-time audio
    // thread. UI/JNI edits are staged under mSeqEditMutex and the final seqSetDrums()
    // call swaps one complete snapshot. The audio callback never waits on a mutex.
    struct SeqSnapshot {
        float bpm=112.f; int top=4,bottom=4; bool loop=true,metro=false;
        bool audNeb=true,audSpec=true,audDrums=true;
        float nebDecay=700.f; int specW1=3,specW2=2;
        std::vector<SeqNeb> neb; std::vector<SeqSpec> spec; std::vector<SeqDrum> drums;
        int contentSteps=16;
    };
    SeqSnapshot mSeqEdit;
    mutable std::mutex mSeqEditMutex;
    struct RetiredSeqSnapshot { const SeqSnapshot* ptr=nullptr; uint32_t epoch=0; };
    std::atomic<const SeqSnapshot*> mSeqPublished{nullptr};
    std::vector<RetiredSeqSnapshot> mSeqRetired; // touched only under mSeqEditMutex
    std::atomic<uint32_t> mRenderEpoch{0};
    std::atomic<bool> mSeqPlaying{false};
    std::atomic<int> mSeqPlayhead{-1};
    // 0 = none, 1 = restart/play, 2 = stop. Consumed only by render().
    std::atomic<int> mSeqCommand{0};
    int mSeqStepRt=0;
    uint64_t mSeqNextFrameRt=0;
    static void seqRecalc(SeqSnapshot& seq);
    void seqPublishEditLocked();
    void seqReclaimLocked();
    void seqFireStep(const SeqSnapshot& seq,int s,uint64_t frame,float stepDur);
    static int seqDrumKind(int midi);
    static float seqDrumPos(int kind,float vel);
    static float seqMidiFreq(int m);
    Queue mQueue;
    std::array<Event,PENDING_MAX> mPending{}; int mPendingN=0;
    std::array<NebVoice,NEB_VOICES> mNeb{};
    std::array<SpecVoice,SPEC_VOICES> mSpec{};
    std::array<DrumVoice,DRUM_VOICES> mDrum{};
    std::array<ClickVoice,CLICK_VOICES> mClick{};
    Rng mRng{};
    std::array<float,NEB_WAVE_LEVELS*NEB_WAVE_SIZE> mNebWaveTables{};

    std::atomic<float> master{.9f},nebVol{.8f},specVol{.8f},drumVol{.9f};
    std::atomic<float> globalVerb{.18f},globalDelayMix{0.f},globalDelayTime{.32f};
    std::atomic<float> nebDlyAmt{0},nebDlyFb{.35f},nebDlyTime{.32f},nebDstAmt{25},nebDstIn{1},nebDstOut{.85f},nebVerbAmt{.35f},nebVerbDecay{2.5f};
    std::atomic<bool> nebDlyOn{false},nebDstOn{false},nebVerbOn{false};
    std::atomic<float> specDlyAmt{0},specDlyFb{.3f},specDlyTime{.25f},specVerbAmt{.35f},specVerbRoom{.5f};
    std::atomic<bool> specDlyOn{false},specVerbOn{false};
    std::atomic<float> drumVerbAmt{.5f}; std::atomic<bool> drumVerbOn{false};

    Delay nebDelay,specDelay,globalDelayL,globalDelayR;
    Reverb nebRev,specRev,roomRev;

    void schedule(Event e,float delaySec);
    void activate(const Event& e);
    void drainForBlock(uint64_t blockEnd);
    void buildNebWaveTables();
    NebVoice& allocNeb(); SpecVoice& allocSpec(); DrumVoice& allocDrum(int kind); ClickVoice& allocClick();
    NebVoice* findLiveNeb(int id);
    SpecVoice* findLiveSpec(int id);
    void configureLiveNeb(NebVoice& v,int midi,float level,float pressure,float muteMix,float cents,float volume,bool starting);
    static float clamp(float x,float a,float b){return x<a?a:(x>b?b:x);}
    static float midiFreq(int m);
    static float nebMuteMix(bool pluck,float vel);
};
