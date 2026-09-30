#include "DspCore.h"
#include <algorithm>
#include <cmath>
#include <cstring>

namespace {
constexpr float PI=3.14159265358979323846f;
inline float soft(float x){return x/(1.f+0.35f*std::fabs(x));}
inline float centsRatio(float cents){return std::pow(2.f,cents/1200.f);}
}

bool DspCore::Queue::push(const Event& e){
    auto h=head.load(std::memory_order_relaxed), n=(h+1)%EVENT_Q;
    if(n==tail.load(std::memory_order_acquire)) return false;
    q[h]=e; head.store(n,std::memory_order_release); return true;
}
bool DspCore::Queue::pop(Event& e){
    auto t=tail.load(std::memory_order_relaxed);
    if(t==head.load(std::memory_order_acquire)) return false;
    e=q[t]; tail.store((t+1)%EVENT_Q,std::memory_order_release); return true;
}

void DspCore::Biquad::lowpass(double sr,float f,float q){
    f=std::clamp(f,20.f,float(sr*.45)); q=std::max(.05f,q);
    float w=2*PI*f/float(sr), c=std::cos(w), s=std::sin(w), a=s/(2*q);
    float A0=1+a; b0=((1-c)*.5f)/A0; b1=(1-c)/A0; b2=b0; a1=(-2*c)/A0; a2=(1-a)/A0;
}
void DspCore::Biquad::bandpass(double sr,float f,float q){
    f=std::clamp(f,20.f,float(sr*.45)); q=std::max(.05f,q);
    float w=2*PI*f/float(sr), c=std::cos(w), s=std::sin(w), a=s/(2*q),A0=1+a;
    b0=a/A0;b1=0;b2=-a/A0;a1=(-2*c)/A0;a2=(1-a)/A0;
}
void DspCore::Biquad::peaking(double sr,float f,float q,float gainDb){
    f=std::clamp(f,20.f,float(sr*.45)); q=std::max(.05f,q); float A=std::pow(10.f,gainDb/40.f);
    float w=2*PI*f/float(sr), c=std::cos(w), s=std::sin(w), a=s/(2*q),A0=1+a/A;
    b0=(1+a*A)/A0;b1=(-2*c)/A0;b2=(1-a*A)/A0;a1=(-2*c)/A0;a2=(1-a/A)/A0;
}
float DspCore::Delay::tick(float x,int ds,float fb){
    if(buf.empty()) return 0; ds=std::clamp(ds,1,int(buf.size()-1)); int r=write-ds; if(r<0) r+=buf.size();
    float y=buf[r]; buf[write]=x+y*fb; if(++write>=int(buf.size()))write=0; return y;
}
float DspCore::Comb::tick(float x,float feedback,float d){float y=b[p];store=y*(1-d)+store*d;b[p]=x+store*feedback;if(++p>=int(b.size()))p=0;return y;}
float DspCore::Allpass::tick(float x,float fb){float y=b[p],o=-x+y;b[p]=x+y*fb;if(++p>=int(b.size()))p=0;return o;}
void DspCore::Reverb::init(double sr){
    const int c[4]={1116,1188,1277,1356}, a[2]={556,441}; float k=float(sr/44100.0);
    for(int i=0;i<4;i++){l[i].init(std::max(8,int(c[i]*k)));r[i].init(std::max(8,int((c[i]+23)*k)));}
    for(int i=0;i<2;i++){al[i].init(std::max(8,int(a[i]*k)));ar[i].init(std::max(8,int((a[i]+23)*k)));}
}
void DspCore::Reverb::clear(){for(auto&x:l)x.clear();for(auto&x:r)x.clear();for(auto&x:al)x.clear();for(auto&x:ar)x.clear();}
void DspCore::Reverb::tick(float in,float fb,float damp,float& L,float& R){
    float a=0,b=0;for(auto&x:l)a+=x.tick(in,fb,damp);for(auto&x:r)b+=x.tick(in,fb,damp);a*=.25f;b*=.25f;
    for(auto&x:al)a=x.tick(a,.5f);for(auto&x:ar)b=x.tick(b,.5f);L=a;R=b;
}

float DspCore::NebVoice::interp(const std::array<float,MAX_POINTS>& t,const std::array<float,MAX_POINTS>& v,int n,float x,float def) const{
    if(n<=0)return def;if(x<=t[0])return v[0];for(int i=1;i<n;i++){if(x<=t[i]){float k=(x-t[i-1])/std::max(1e-6f,t[i]-t[i-1]);return v[i-1]+(v[i]-v[i-1])*k;}}return v[n-1];
}
float DspCore::NebVoice::sample(double sr,uint64_t frame,Rng& rng,const float* waveTables){
    if(!active||frame<start)return 0;
    uint64_t n=frame-start; age=n; float t=float(n/sr);

    if(live){
        // Smooth retunes and live expression enough to avoid zippering while still feeling immediate.
        currentFreq += (targetFreq-currentFreq)*.0045f;
        currentAmp += (targetAmp-currentAmp)*.0040f;
        currentWaveMix += (waveMix-currentWaveMix)*.0040f;
        phase+=2*PI*currentFreq/float(sr); if(phase>2*PI)phase=std::fmod(phase,2*PI);
        float pos=phase*(float(NEB_WAVE_SIZE)/(2*PI));
        int ix=int(pos)&(NEB_WAVE_SIZE-1), ix2=(ix+1)&(NEB_WAVE_SIZE-1); float xf=pos-float(int(pos));
        float wm=std::clamp(currentWaveMix,0.f,float(NEB_WAVE_LEVELS-1)); int w0=int(wm), w1=std::min(w0+1,NEB_WAVE_LEVELS-1); float wf=wm-w0;
        const float* a=waveTables+w0*NEB_WAVE_SIZE; const float* b=waveTables+w1*NEB_WAVE_SIZE;
        float sa=a[ix]+(a[ix2]-a[ix])*xf, sb=b[ix]+(b[ix2]-b[ix])*xf; float harm=sa+(sb-sa)*wf;
        float env=currentAmp;
        if(pluck){
            float d=std::max(.003f,rel*(mute>0?std::max(.08f,1-.92f*mute):1.f));
            env=currentAmp*std::exp(-6.9f*t/d);
            if(t>d+.08f){active=false;return 0;}
        }else if(releasing){
            float rt=float((frame-releaseFrame)/sr);
            env=currentAmp*std::exp(-6.9f*rt/std::max(.01f,rel));
            if(rt>std::max(.03f,rel*1.25f)){active=false;return 0;}
        }else{
            if(t<.024f) env*=.78f*(t/.024f);
            else if(t<.085f) env*=.78f+.22f*(t-.024f)/.061f;
        }
        float y=smooth.process(body.process(harm))*env;
        if(!pluck){
            float w=rng.next();noiseState=noiseState*.82f+w*.18f;
            // In WebAudio the bow-noise branch joins the signal before voiceGain, so it follows
            // the same attack/release envelope as the pitched string instead of hanging as a hiss.
            y+=noiseBand.process(noiseState)*(.004f+(1-pressure)*.012f+pressure*.0065f)*env;
        }else{
            float tr=std::exp(-t/0.018f); y+=rng.next()*tr*(.02f+.02f*mute)*env;
        }
        return y;
    }

    float norm=dur>1e-5f?std::clamp(t/dur,0.f,1.f):1.f;
    float bend=interp(bt,bv,bn,norm,0), vex=interp(vt,vv,vn,norm,vel); float f=baseFreq*std::pow(2.f,bend/12.f);
    phase+=2*PI*f/float(sr); if(phase>2*PI)phase=std::fmod(phase,2*PI);
    float pos=phase*(float(NEB_WAVE_SIZE)/(2*PI));
    int ix=int(pos)&(NEB_WAVE_SIZE-1), ix2=(ix+1)&(NEB_WAVE_SIZE-1); float xf=pos-float(int(pos));
    float wm=std::clamp(waveMix,0.f,float(NEB_WAVE_LEVELS-1)); int w0=int(wm), w1=std::min(w0+1,NEB_WAVE_LEVELS-1); float wf=wm-w0;
    const float* a=waveTables+w0*NEB_WAVE_SIZE; const float* b=waveTables+w1*NEB_WAVE_SIZE;
    float sa=a[ix]+(a[ix2]-a[ix])*xf, sb=b[ix]+(b[ix2]-b[ix])*xf; float harm=sa+(sb-sa)*wf;
    float env=0;
    if(pluck){float d=std::max(.003f,rel*(mute>0?std::max(.08f,1-.92f*mute):1.f));env=onset*std::exp(-6.9f*t/d);if(t>d+.08f){active=false;return 0;}}
    else{if(t<.024f)env=onset*.78f*(t/.024f);else if(t<.085f)env=onset*(.78f+.22f*(t-.024f)/.061f);else if(t<=dur)env=onset;else{float rt=t-dur;env=onset*std::exp(-6.9f*rt/std::max(.05f,rel));if(rt>rel*1.25f){active=false;return 0;}}}
    float vscale=std::clamp(vex/std::max(.05f,vel),0.f,1.25f);
    float y=smooth.process(body.process(harm))*env*vscale;
    if(!pluck){float w=rng.next();noiseState=noiseState*.82f+w*.18f; y+=noiseBand.process(noiseState)*(.004f+(1-pressure)*.012f+pressure*.0065f)*env*vscale;}
    else { float tr=std::exp(-t/0.018f); y+=rng.next()*tr*(.02f+.02f*mute)*env; }
    return y;
}
float DspCore::SpecVoice::sample(double sr,uint64_t frame){
    if(!active||frame<start)return 0;
    age=frame-start;
    float e=1.f;
    if(live){
        if(releasing){
            float rt=float((frame-releaseFrame)/sr); e=std::exp(-6.9f*rt/.1f);
            if(rt>.13f){active=false;return 0;}
        }
    }else{
        float t=float(age/sr),rel=.1f;if(t>dur+rel){active=false;return 0;}e=t<=dur?1.f:std::exp(-6.9f*(t-dur)/rel);
    }
    phase+=2*PI*freq/float(sr);if(phase>2*PI)phase-=2*PI;float y=0;
    switch(wave){case 0:y=std::sin(phase);break;case 1:y=2.f/PI*std::asin(std::sin(phase));break;case 2:y=2.f*(phase/(2*PI))-1.f;break;default:y=std::sin(phase)>=0?1.f:-1.f;break;}
    return y*(live?liveGain:(.1f*vel))*e;
}
float DspCore::DrumVoice::sample(double sr,uint64_t frame){
    if(!active||frame<start)return 0;age=frame-start;float t=float(age/sr);float out=0;
    if(t>life){active=false;return 0;}
    auto osc=[&](int i,float f){phase[i]+=2*PI*f/float(sr);if(phase[i]>2*PI)phase[i]-=2*PI;return std::sin(phase[i]);};
    if(kind==0){
        float hard=1-pos,soft=pos,low=std::clamp((130-freq)/130.f,0.f,1.f);float body=.48f+vel*.34f+soft*.15f+low*.18f, fund=body*1.8f;
        float ff=freq*(1.f+(.72f+.22f*hard)*std::exp(-t/.052f));out+=osc(0,ff)*vel*(1+soft*.15f+low*.25f)*std::exp(-5.8f*t/fund);
        const float r[5]={2,1.593f,2.136f,2.296f,.5f},g[5]={.16f,.16f,.085f,.055f,.32f};for(int i=0;i<5;i++)out+=osc(i+1,freq*r[i])*vel*g[i]*std::exp(-7.f*t/(.12f+body*.5f));
        float noiseDec=.026f+soft*.012f;
        out+=n1.process(rng.next())*vel*(.12f+.12f*hard)*std::exp(-7.f*t/std::max(.006f,noiseDec));life=fund+.2f;
    }else if(kind==1){
        float edge=std::clamp((pos-.28f)/.66f,0.f,1.f);edge=edge*edge*(3-2*edge);float center=1-edge;const float r[6]={1,1.593f,2.136f,2.296f,2.653f,2.918f},g[6]={1,.34f,.2f,.13f,.09f,.06f};
        float body=.29f+vel*.15f;for(int i=0;i<6;i++)out+=osc(i,freq*r[i])*vel*g[i]*(i?(.82f+edge*.42f):(1-.48f*edge))*std::exp(-6.f*t/(body/(1+i*.25f)*(i? .82f:1.8f)));
        float w=vel*(.52f+.16f*center+.20f*edge);
        float noiseDec=.16f+center*.08f;
        out+=(n1.process(rng.next())*.72f+n2.process(rng.next())*.62f+n3.process(rng.next())*.34f)*w*std::exp(-7.f*t/std::max(.02f,noiseDec));life=.65f;
    }else{
        float bell=1-std::clamp((pos-.24f)/.25f,0.f,1.f);bell=bell*bell*(3-2*bell);float edge=std::clamp((pos-.61f)/.29f,0.f,1.f);edge=edge*edge*(3-2*edge);float bow=std::max(0.f,1-bell-edge);
        float decay=bell*(1.15f+vel*.75f)+bow*(1.45f+vel*.9f)+edge*(2.2f+vel*1.f+std::clamp((pos-.62f)/.38f,0.f,1.f)*.7f);decay=std::max(.5f,decay);life=decay+.25f;
        const float ratios[8]={1,1.71f,2.74f,4.16f,1.82f,3.82f,5.2f,6.4f};
        for(int i=0;i<8;i++){float amp=(i==0?.26f:.05f/(1+i*.3f))*(bell+bow*.75f+edge*.55f);float drift=1.f+jitter[i]*.003f;out+=osc(i,freq*ratios[i]*drift)*vel*amp*std::exp(-3.8f*t/(decay*(i==0?1.f:.55f)));}
        float wash=(bell*.10f+bow*.30f+edge*.45f)*vel;
        float noiseDec=std::max(.05f,decay*.92f);
        out+=(n1.process(rng.next())*.45f+n2.process(rng.next())*.32f+n3.process(rng.next())*.2f)*wash*std::exp(-7.f*t/noiseDec);
    }
    return out;
}
float DspCore::ClickVoice::sample(double sr,uint64_t frame){if(!active||frame<start)return 0;float t=float((frame-start)/sr);if(t>.06f){active=false;return 0;}phase+=2*PI*freq/float(sr);return std::sin(phase)*.25f*std::exp(-70.f*t);}

DspCore::DspCore(){
    mSeqPublished.store(new SeqSnapshot(mSeqEdit),std::memory_order_release);
}
DspCore::~DspCore(){
    const SeqSnapshot* current=mSeqPublished.exchange(nullptr,std::memory_order_acq_rel);
    delete current;
    for(auto&r:mSeqRetired)delete r.ptr;
    mSeqRetired.clear();
}
void DspCore::buildNebWaveTables(){
    static const float open0[6]={1,.7f,.5f,.38f,.28f,.2f}, palm0[6]={1.55f,1.18f,.78f,.4f,.18f,.08f};
    for(int wi=0;wi<NEB_WAVE_LEVELS;wi++){
        float mute=float(wi)/float(NEB_WAVE_LEVELS-1);
        float amps[18],o=open0[5],p=palm0[5],nm=0;
        for(int h=0;h<18;h++){
            if(h<6){o=open0[h];p=palm0[h];}else{o*=.78f;p*=.46f;}
            amps[h]=o*(1-mute)+p*mute; nm+=std::fabs(amps[h]);
        }
        float norm=std::max(1.f,nm*.42f);
        for(int i=0;i<NEB_WAVE_SIZE;i++){
            float ph=2*PI*float(i)/float(NEB_WAVE_SIZE),y=0;
            for(int h=0;h<18;h++) y+=amps[h]*std::sin(ph*float(h+1));
            mNebWaveTables[wi*NEB_WAVE_SIZE+i]=y/norm;
        }
    }
}
float DspCore::midiFreq(int m){return 440.f*std::pow(2.f,(m-69)/12.f);}
float DspCore::nebMuteMix(bool p,float v){if(!p)return 0;constexpr float lo=.05f,hi=1.15f,b1=lo+(hi-lo)/3,b2=lo+2*(hi-lo)/3;v=clamp(v,lo,hi);if(v<=b1)return 1;if(v>=b2)return 0;return (b2-v)/(b2-b1);}
void DspCore::prepare(double sr){mSampleRate=sr>1000?sr:48000;buildNebWaveTables();int maxDelay=int(mSampleRate*2.1);nebDelay.init(maxDelay);specDelay.init(maxDelay);globalDelayL.init(maxDelay);globalDelayR.init(maxDelay);nebRev.init(mSampleRate);specRev.init(mSampleRate);roomRev.init(mSampleRate);reset();}
void DspCore::reset(){ mSeqPlaying.store(false,std::memory_order_release);mSeqPlayhead.store(-1,std::memory_order_release);mSeqCommand.store(0,std::memory_order_release);mSeqStepRt=0;mSeqNextFrameRt=0;for(auto&v:mNeb)v.active=false;for(auto&v:mSpec)v.active=false;for(auto&v:mDrum)v.active=false;for(auto&v:mClick)v.active=false;mPendingN=0;mFrame.store(0);nebDelay.clear();specDelay.clear();globalDelayL.clear();globalDelayR.clear();nebRev.clear();specRev.clear();roomRev.clear();}
void DspCore::schedule(Event e,float delaySec){e.frame=mFrame.load(std::memory_order_relaxed)+uint64_t(std::max(0.f,delaySec)*mSampleRate);mQueue.push(e);}
DspCore::NebVoice& DspCore::allocNeb(){for(auto&v:mNeb)if(!v.active)return v;return *std::min_element(mNeb.begin(),mNeb.end(),[](auto&a,auto&b){return a.age>b.age;});}
DspCore::SpecVoice& DspCore::allocSpec(){for(auto&v:mSpec)if(!v.active)return v;return *std::min_element(mSpec.begin(),mSpec.end(),[](auto&a,auto&b){return a.age>b.age;});}
DspCore::DrumVoice& DspCore::allocDrum(int kind){if(kind==2){int n=0;DrumVoice*old=nullptr;for(auto&v:mDrum)if(v.active&&v.kind==2){n++;if(!old||v.age>old->age)old=&v;}if(n>=3&&old)return *old;}for(auto&v:mDrum)if(!v.active)return v;return *std::min_element(mDrum.begin(),mDrum.end(),[](auto&a,auto&b){return a.age>b.age;});}
DspCore::ClickVoice& DspCore::allocClick(){for(auto&v:mClick)if(!v.active)return v;return mClick[0];}
DspCore::NebVoice* DspCore::findLiveNeb(int id){for(auto&v:mNeb)if(v.active&&v.live&&v.liveId==id)return &v;return nullptr;}
DspCore::SpecVoice* DspCore::findLiveSpec(int id){for(auto&v:mSpec)if(v.active&&v.live&&v.liveId==id)return &v;return nullptr;}

void DspCore::configureLiveNeb(NebVoice& v,int midi,float level,float pressure,float muteMix,float cents,float volume,bool starting){
    v.baseFreq=midiFreq(midi);
    v.targetFreq=v.baseFreq*centsRatio(cents);
    if(starting) v.currentFreq=v.targetFreq;
    v.pressure=clamp(pressure,0.f,1.f);
    v.mute=clamp(muteMix,0.f,1.f);
    v.liveVolume=clamp(volume,0.f,2.f);
    level=clamp(level,0.f,1.15f);
    float mult=(!v.pluck&&v.mute<=.0001f)?(.95f+.12f*v.pressure):(1.f+.26f*v.mute);
    v.targetAmp=std::max(.0001f,(.03f+.21f*level)*mult*v.liveVolume);
    if(starting) v.currentAmp=0.f;

    // Match the original WebAudio periodic-wave behavior without rebuilding 18 harmonics per sample.
    v.waveMix=v.mute*float(NEB_WAVE_LEVELS-1);
    if(starting) v.currentWaveMix=v.waveMix;
    // Pitch-bend-only motion should not rebuild three biquads. Recalculate the tone filters
    // only when the cell/pressure/mute actually changes enough to matter.
    const bool toneChanged = starting
        || std::fabs(v.baseFreq-v.filterBaseFreq)>0.01f
        || std::fabs(v.pressure-v.filterPressure)>0.004f
        || std::fabs(v.mute-v.filterMute)>0.004f;
    if(toneChanged){
        float f=v.baseFreq,c,q,g,s;
        if(!v.pluck&&v.mute<=.0001f){
            c=std::min(3200.f,std::max(160.f,f*(3+1.5f*v.pressure)));
            q=.8f+1.8f*v.pressure; g=4+4.8f*v.pressure; s=1500+4200*v.pressure;
        }else{
            c=std::min(2200.f,std::max(120.f,f*(4-2.3f*v.mute)));
            q=1+5.4f*v.mute; g=5.8f+7.8f*v.mute; s=5200*(1-v.mute)+1350*v.mute;
        }
        v.body.peaking(mSampleRate,c,q,g);
        v.smooth.lowpass(mSampleRate,s,.2f);
        v.noiseBand.bandpass(mSampleRate,700+v.pressure*2100,.5f+v.pressure*1.3f);
        v.filterBaseFreq=v.baseFreq; v.filterPressure=v.pressure; v.filterMute=v.mute;
    }
}

void DspCore::activate(const Event&e){
    if(e.type==EventType::Neb){
        auto&v=allocNeb();v=NebVoice{};v.active=true;v.start=e.frame;v.baseFreq=midiFreq(e.i0);v.vel=clamp(e.a,.05f,1.15f);v.dur=std::max(.01f,e.b);v.pluck=e.i1!=0;v.rel=std::max(.05f,e.c/1000.f);v.mute=nebMuteMix(v.pluck,v.vel);bool palm=v.mute>=.999f;float trim=v.vel>1?v.vel:1;v.pressure=v.pluck?(palm?1:clamp(v.vel,0,1)):clamp((v.vel-.05f)/1.1f,0,1);float level=v.pluck?(palm?1:clamp(v.vel,0,1)*(1-v.mute)+v.mute):.06f+std::pow(v.pressure,2.35f)*.94f;float mult=(!v.pluck&&!v.mute)?.95f+.12f*v.pressure:1+.26f*v.mute;v.onset=std::max(.0001f,(.03f+.21f*level)*mult*trim);
        v.waveMix=v.mute*float(NEB_WAVE_LEVELS-1);
        float f=v.baseFreq,c=v.pluck?std::min(2200.f,std::max(120.f,f*(4-2.3f*v.mute))):std::min(3200.f,std::max(160.f,f*(3+1.5f*v.pressure)));float q=v.pluck?1+5.4f*v.mute:.8f+1.8f*v.pressure,g=v.pluck?5.8f+7.8f*v.mute:4+4.8f*v.pressure,s=v.pluck?5200*(1-v.mute)+1350*v.mute:1500+4200*v.pressure;v.body.peaking(mSampleRate,c,q,g);v.smooth.lowpass(mSampleRate,s,.2f);v.noiseBand.bandpass(mSampleRate,700+v.pressure*2100,.5f+v.pressure*1.3f);v.bn=e.n1;v.vn=e.n2;v.bt=e.t1;v.bv=e.v1;v.vt=e.t2;v.vv=e.v2;
    }else if(e.type==EventType::Spec){
        auto&v=allocSpec();v=SpecVoice{};v.active=true;v.start=e.frame;v.freq=midiFreq(e.i0);v.vel=clamp(e.a,.05f,1.15f);v.dur=std::max(.01f,e.b);v.wave=e.i1;
    }else if(e.type==EventType::Drum){
        auto&v=allocDrum(e.i0);v=DrumVoice{};v.active=true;v.start=e.frame;v.kind=e.i0;v.freq=std::max(20.f,e.a);v.vel=clamp(e.b,.05f,1.15f);v.pos=clamp(e.c,0,1);for(int i=0;i<8;i++)v.jitter[i]=v.rng.next();if(v.kind==0){v.n1.bandpass(mSampleRate,1700+(1-v.pos)*900,1.0f);v.life=2;}else if(v.kind==1){float edge=clamp((v.pos-.28f)/.66f,0,1);v.n1.bandpass(mSampleRate,1750,.62f);v.n2.bandpass(mSampleRate,3550,.78f);v.n3.bandpass(mSampleRate,6500,.52f);v.life=.7f;}else{v.n1.bandpass(mSampleRate,2400,.62f);v.n2.bandpass(mSampleRate,4300,.72f);v.n3.bandpass(mSampleRate,7600,.75f);v.life=4.5f;}
    }else if(e.type==EventType::Click){
        auto&v=allocClick();v=ClickVoice{};v.active=true;v.start=e.frame;v.freq=e.i0?2000:1400;
    }else if(e.type==EventType::NebLiveStart){
        if(auto*old=findLiveNeb(e.i0)) old->active=false;
        auto&v=allocNeb();v=NebVoice{};v.active=true;v.live=true;v.liveId=e.i0;v.start=e.frame;v.pluck=e.n1!=0;v.rel=std::max(.01f,e.f/1000.f);v.releasing=false;
        configureLiveNeb(v,e.i1,e.a,e.b,e.c,e.d,e.e,true);
    }else if(e.type==EventType::NebLiveUpdate){
        if(auto*v=findLiveNeb(e.i0)) configureLiveNeb(*v,e.i1,e.a,e.b,e.c,e.d,e.e,false);
    }else if(e.type==EventType::NebLiveStop){
        if(auto*v=findLiveNeb(e.i0)){v->rel=std::max(.01f,e.a/1000.f);v->releasing=true;v->releaseFrame=e.frame;}
    }else if(e.type==EventType::SpecLiveStart){
        if(auto*old=findLiveSpec(e.i0)) old->active=false;
        auto&v=allocSpec();v=SpecVoice{};v.active=true;v.live=true;v.liveId=e.i0;v.start=e.frame;v.freq=midiFreq(e.i1);v.liveGain=clamp(e.a,0.f,1.f);v.wave=int(e.n1);v.releasing=false;
    }else if(e.type==EventType::SpecLiveStop){
        if(auto*v=findLiveSpec(e.i0)){v->releasing=true;v->releaseFrame=e.frame;}
    }else if(e.type==EventType::LivePanic){
        for(auto&v:mNeb) if(v.live) v.active=false;
        for(auto&v:mSpec) if(v.live) v.active=false;
    }
}
void DspCore::drainForBlock(uint64_t end){Event e;while(mPendingN<PENDING_MAX&&mQueue.pop(e))mPending[mPendingN++]=e;int w=0;for(int i=0;i<mPendingN;i++){if(mPending[i].frame<end)activate(mPending[i]);else mPending[w++]=mPending[i];}mPendingN=w;}
void DspCore::render(float*out,int32_t frames){if(!out||frames<=0)return;mRenderEpoch.fetch_add(1,std::memory_order_acq_rel);uint64_t base=mFrame.load(std::memory_order_relaxed),end=base+frames;drainForBlock(end);
    const int seqCmd=mSeqCommand.exchange(0,std::memory_order_acq_rel);
    if(seqCmd==1){
        mSeqStepRt=0;
        mSeqPlayhead.store(-1,std::memory_order_release);
        mSeqNextFrameRt=base+(uint64_t)(mSampleRate*0.08);
        mSeqPlaying.store(true,std::memory_order_release);
    }else if(seqCmd==2){
        mSeqPlaying.store(false,std::memory_order_release);
        mSeqPlayhead.store(-1,std::memory_order_release);
    }
    if(mSeqPlaying.load(std::memory_order_acquire)){
        const SeqSnapshot* seq=mSeqPublished.load(std::memory_order_acquire);
        if(seq){
            if(mSeqStepRt>=seq->contentSteps)mSeqStepRt=0;
            float sd=60.f/std::max(1.f,seq->bpm)/(float)std::max(1,seq->bottom);
            int guard=0;
            while(mSeqPlaying.load(std::memory_order_relaxed)&&mSeqNextFrameRt<end&&guard++<32){
                seqFireStep(*seq,mSeqStepRt,mSeqNextFrameRt,sd);
                mSeqPlayhead.store(mSeqStepRt,std::memory_order_release);
                uint64_t adv=(uint64_t)(sd*mSampleRate);if(adv<1)adv=1;
                mSeqNextFrameRt+=adv;
                mSeqStepRt++;
                if(mSeqStepRt>=seq->contentSteps){
                    if(seq->loop)mSeqStepRt=0;
                    else{mSeqPlaying.store(false,std::memory_order_release);mSeqPlayhead.store(-1,std::memory_order_release);}
                }
            }
        }
    }
    const float m=master.load(),nv=nebVol.load(),sv=specVol.load(),dv=drumVol.load();
    for(int i=0;i<frames;i++){uint64_t fr=base+i;float n=0,s=0,d=0,c=0;for(auto&v:mNeb)n+=v.sample(mSampleRate,fr,mRng,mNebWaveTables.data());for(auto&v:mSpec)s+=v.sample(mSampleRate,fr);for(auto&v:mDrum)d+=v.sample(mSampleRate,fr);for(auto&v:mClick)c+=v.sample(mSampleRate,fr);
        n*=nv;s*=sv;d*=dv;d=soft(d*1.12f);
        float npost=n;if(nebDstOn.load()){float x=n*nebDstIn.load(),k=std::max(0.f,nebDstAmt.load())*2.2f;npost=(k<=0?x:((1+k)*x)/(1+k*std::fabs(x)))*nebDstOut.load();}
        if(nebDlyOn.load()){int ds=int(clamp(nebDlyTime.load(),.05f,1.5f)*mSampleRate);npost+=nebDelay.tick(npost,ds,clamp(nebDlyFb.load(),0,.95f))*nebDlyAmt.load();}else nebDelay.tick(npost,int(.32*mSampleRate),0);
        if(specDlyOn.load()){int ds=int(clamp(specDlyTime.load(),.05f,1.5f)*mSampleRate);s+=specDelay.tick(s,ds,clamp(specDlyFb.load(),0,.95f))*specDlyAmt.load();}else specDelay.tick(s,int(.25*mSampleRate),0);
        float rl=0,rr=0,xl=0,xr=0,yl=0,yr=0;
        float ns=nebVerbOn.load()?nebVerbAmt.load():.35f;float ss=specVerbOn.load()?specVerbAmt.load():.35f;float ds=drumVerbOn.load()?drumVerbAmt.load():.5f;
        nebRev.tick(npost*ns,clamp(.68f+nebVerbDecay.load()*.025f,.68f,.88f),.28f,xl,xr);
        specRev.tick(s*ss,clamp(.70f+specVerbRoom.load()*.12f,.70f,.86f),.26f,yl,yr);
        roomRev.tick(d*ds, .78f,.34f,rl,rr);
        float mix=npost+s+d+c + (xl+yl+rl)*.55f + globalVerb.load()*(rl+xl+yl)*.25f;
        int gd=int(clamp(globalDelayTime.load(),.05f,1.2f)*mSampleRate);float gdl=globalDelayL.tick(mix,gd,.35f),gdr=globalDelayR.tick(mix,gd+17,.35f);float gm=globalDelayMix.load();
        float L=clamp(soft((mix+gdl*gm*.6f)*m),-.98f,.98f),R=clamp(soft((npost+s+d+c+(xr+yr+rr)*.55f+gdr*gm*.6f)*m),-.98f,.98f);out[i*2]=L;out[i*2+1]=R;
    }mFrame.store(end,std::memory_order_relaxed);
}
void DspCore::setMaster(float v){master.store(clamp(v,0,1.5f));}
void DspCore::setTrack(int t,float v){v=clamp(v,0,1.5f);if(t==0)nebVol.store(v);else if(t==1)specVol.store(v);else drumVol.store(v);}
void DspCore::setGlobalReverb(float v){globalVerb.store(clamp(v,0,1));}
void DspCore::setGlobalDelayMix(float v){globalDelayMix.store(clamp(v,0,1));}
void DspCore::setGlobalDelayTime(float v){globalDelayTime.store(clamp(v,.05f,1.2f));}
void DspCore::setNebFx(bool a,float b,float c,float d,bool e,float f,float g,float h,bool i,float j,float k){nebDlyOn=a;nebDlyAmt=b;nebDlyFb=c;nebDlyTime=d;nebDstOn=e;nebDstAmt=f;nebDstIn=g;nebDstOut=h;nebVerbOn=i;nebVerbAmt=j;nebVerbDecay=k;}
void DspCore::setSpecFx(bool a,float b,float c,float d,bool e,float f,float g){specDlyOn=a;specDlyAmt=b;specDlyFb=c;specDlyTime=d;specVerbOn=e;specVerbAmt=f;specVerbRoom=g;}
void DspCore::setDrumFx(bool a,float b){drumVerbOn=a;drumVerbAmt=b;}
void DspCore::nebNote(int midi,float vel,float dur,float delay,bool pluck,float decay,const float*bt,const float*bv,int bn,const float*vt,const float*vv,int vn){Event e;e.type=EventType::Neb;e.i0=midi;e.i1=pluck;e.a=vel;e.b=dur;e.c=decay;e.n1=std::min(bn,MAX_POINTS);e.n2=std::min(vn,MAX_POINTS);for(int i=0;i<e.n1;i++){e.t1[i]=bt[i];e.v1[i]=bv[i];}for(int i=0;i<e.n2;i++){e.t2[i]=vt[i];e.v2[i]=vv[i];}schedule(e,delay);}
void DspCore::specNote(int midi,float vel,float dur,float delay,int wave){Event e;e.type=EventType::Spec;e.i0=midi;e.i1=wave;e.a=vel;e.b=dur;schedule(e,delay);}
void DspCore::drumHit(int kind,float freq,float vel,float pos,float delay){Event e;e.type=EventType::Drum;e.i0=kind;e.a=freq;e.b=vel;e.c=pos;schedule(e,delay);}
void DspCore::click(bool hi,float delay){Event e;e.type=EventType::Click;e.i0=hi;schedule(e,delay);}
void DspCore::nebLiveStart(int id,int midi,float level,float pressure,float muteMix,float cents,float volume,bool pluck,float releaseMs){Event e;e.type=EventType::NebLiveStart;e.i0=id;e.i1=midi;e.a=level;e.b=pressure;e.c=muteMix;e.d=cents;e.e=volume;e.f=releaseMs;e.n1=pluck?1:0;schedule(e,0);}
void DspCore::nebLiveUpdate(int id,int midi,float level,float pressure,float muteMix,float cents,float volume){Event e;e.type=EventType::NebLiveUpdate;e.i0=id;e.i1=midi;e.a=level;e.b=pressure;e.c=muteMix;e.d=cents;e.e=volume;schedule(e,0);}
void DspCore::nebLiveStop(int id,float releaseMs){Event e;e.type=EventType::NebLiveStop;e.i0=id;e.a=releaseMs;schedule(e,0);}
void DspCore::specLiveStart(int id,int midi,float gain,int wave){Event e;e.type=EventType::SpecLiveStart;e.i0=id;e.i1=midi;e.a=gain;e.n1=uint8_t(std::clamp(wave,0,3));schedule(e,0);}
void DspCore::specLiveStop(int id){Event e;e.type=EventType::SpecLiveStop;e.i0=id;schedule(e,0);}
void DspCore::livePanic(){Event e;e.type=EventType::LivePanic;schedule(e,0);}

void DspCore::seqRecalc(SeqSnapshot& seq){
    int bl=std::max(1,seq.bottom),bar=std::max(1,seq.top*bl);
    int last=-1;
    for(auto&x:seq.neb)last=std::max(last,x.s+std::max(1,x.len)-1);
    for(auto&x:seq.spec)last=std::max(last,x.s+std::max(1,x.len)-1);
    for(auto&x:seq.drums)last=std::max(last,x.s);
    int bars=std::max(1,(int)std::ceil((last+1)/(float)bar));
    seq.contentSteps=std::max(1,bars*bar);
}
void DspCore::seqReclaimLocked(){
    const uint32_t now=mRenderEpoch.load(std::memory_order_acquire);
    size_t w=0;
    for(size_t i=0;i<mSeqRetired.size();i++){
        auto&r=mSeqRetired[i];
        // A snapshot is used only inside one render callback. Waiting for two later
        // callback epochs before deletion gives the audio thread an RCU grace period.
        if(now>r.epoch+2)delete r.ptr;
        else mSeqRetired[w++]=r;
    }
    mSeqRetired.resize(w);
}
void DspCore::seqPublishEditLocked(){
    const SeqSnapshot* next=new SeqSnapshot(mSeqEdit);
    const SeqSnapshot* old=mSeqPublished.exchange(next,std::memory_order_acq_rel);
    if(old)mSeqRetired.push_back({old,mRenderEpoch.load(std::memory_order_acquire)});
    seqReclaimLocked();
}
int DspCore::seqDrumKind(int midi){ if(midi>=40&&midi<=46) return 0; if(midi>=47&&midi<=53) return 1; return 2; }
float DspCore::seqMidiFreq(int m){ return 440.f*std::pow(2.f,(m-69)/12.f); }
float DspCore::seqDrumPos(int kind,float vel){
    constexpr float lo=.05f,hi=1.15f,b1=lo+(hi-lo)/3.f,b2=lo+2.f*(hi-lo)/3.f;
    float v=std::clamp(vel,lo,hi);
    float a0,a1,a2;
    if(kind==2){ a0=.08f; a1=.5f; a2=.97f; } else if(kind==1){ a0=.9f; a1=.5f; a2=.08f; } else { a0=.85f; a1=.4f; a2=.06f; }
    float c0=(lo+b1)*.5f,c1=(b1+b2)*.5f,c2=(b2+hi)*.5f,p;
    if(v<=c0)p=a0;else if(v<=c1)p=a0+(a1-a0)*(v-c0)/std::max(1e-6f,c1-c0);
    else if(v<=c2)p=a1+(a2-a1)*(v-c1)/std::max(1e-6f,c2-c1);else p=a2;
    return std::clamp(p,std::min(a0,a2),std::max(a0,a2));
}
void DspCore::seqConfigure(float bpm,int top,int bottom,bool loop,bool metro){
    std::lock_guard<std::mutex> lk(mSeqEditMutex);
    mSeqEdit.bpm=std::clamp(bpm,12.f,240.f);mSeqEdit.top=std::clamp(top,1,24);mSeqEdit.bottom=std::clamp(bottom,1,24);
    mSeqEdit.loop=loop;mSeqEdit.metro=metro;seqRecalc(mSeqEdit);
}
void DspCore::seqSetAudible(bool neb,bool spec,bool drums){std::lock_guard<std::mutex>lk(mSeqEditMutex);mSeqEdit.audNeb=neb;mSeqEdit.audSpec=spec;mSeqEdit.audDrums=drums;}
void DspCore::seqSetNebDecay(float decayMs){std::lock_guard<std::mutex>lk(mSeqEditMutex);mSeqEdit.nebDecay=std::clamp(decayMs,50.f,3000.f);}
void DspCore::seqSetSpecWaves(int w1,int w2){std::lock_guard<std::mutex>lk(mSeqEditMutex);mSeqEdit.specW1=std::clamp(w1,0,3);mSeqEdit.specW2=std::clamp(w2,0,3);}
void DspCore::seqClear(){
    std::lock_guard<std::mutex>lk(mSeqEditMutex);
    mSeqEdit.neb.clear();mSeqEdit.spec.clear();mSeqEdit.drums.clear();seqRecalc(mSeqEdit);seqPublishEditLocked();
}
void DspCore::seqSetNeb(int n,const int* s,const int* midi,const int* len,const float* vel,
    const unsigned char* pluck,const int* bendN,const float* bendT,const float* bendV,
    const int* vexN,const float* vexT,const float* vexV){
    std::vector<SeqNeb> v;n=std::clamp(n,0,4096);v.reserve(n);
    for(int i=0;i<n;i++){SeqNeb x;x.s=s[i];x.midi=midi[i];x.len=std::clamp(len[i],1,16);
        x.vel=std::clamp(vel[i],.05f,1.15f);x.pluck=pluck[i]!=0;
        int bn=std::clamp(bendN[i],0,MAX_POINTS);x.bn=(uint8_t)bn;
        for(int k=0;k<bn;k++){x.bt[k]=std::clamp(bendT[i*MAX_POINTS+k],0.f,1.f);x.bv[k]=std::clamp(bendV[i*MAX_POINTS+k],-12.f,12.f);}
        int vn=std::clamp(vexN[i],0,MAX_POINTS);x.vn=(uint8_t)vn;
        for(int k=0;k<vn;k++){x.vt[k]=std::clamp(vexT[i*MAX_POINTS+k],0.f,1.f);x.vv[k]=std::clamp(vexV[i*MAX_POINTS+k],0.f,1.15f);}
        v.push_back(x);
    }
    std::lock_guard<std::mutex>lk(mSeqEditMutex);mSeqEdit.neb=std::move(v);seqRecalc(mSeqEdit);
}
void DspCore::seqSetSpec(int n,const int* s,const int* midi1,const int* midi2,const int* len,const float* vel){
    std::vector<SeqSpec> v;n=std::clamp(n,0,4096);v.reserve(n);
    for(int i=0;i<n;i++){SeqSpec x;x.s=s[i];x.midi1=midi1[i];x.midi2=midi2[i];x.len=std::clamp(len[i],1,16);x.vel=std::clamp(vel[i],.05f,1.15f);v.push_back(x);}
    std::lock_guard<std::mutex>lk(mSeqEditMutex);mSeqEdit.spec=std::move(v);seqRecalc(mSeqEdit);
}
void DspCore::seqSetDrums(int n,const int* s,const int* midi,const float* vel){
    std::vector<SeqDrum> v;n=std::clamp(n,0,4096);v.reserve(n);
    for(int i=0;i<n;i++){SeqDrum x;x.s=s[i];x.midi=midi[i];x.vel=std::clamp(vel[i],.05f,1.15f);v.push_back(x);}
    std::lock_guard<std::mutex>lk(mSeqEditMutex);
    mSeqEdit.drums=std::move(v);seqRecalc(mSeqEdit);
    // RCU publication: allocation/copy/reclamation stay off the real-time callback.
    seqPublishEditLocked();
}
void DspCore::seqPlay(){mSeqPlayhead.store(-1,std::memory_order_release);mSeqPlaying.store(true,std::memory_order_release);mSeqCommand.store(1,std::memory_order_release);}
void DspCore::seqStop(){mSeqPlaying.store(false,std::memory_order_release);mSeqPlayhead.store(-1,std::memory_order_release);mSeqCommand.store(2,std::memory_order_release);}
bool DspCore::seqIsPlaying() const{return mSeqPlaying.load(std::memory_order_acquire);}
int DspCore::seqStep() const{return mSeqPlayhead.load(std::memory_order_acquire);}
void DspCore::seqFireStep(const SeqSnapshot& seq,int st,uint64_t frame,float stepDur){
    int beatLen=std::max(1,seq.bottom),barLen=std::max(1,seq.top*beatLen);
    if(seq.metro&&(st%beatLen==0)){Event e;e.type=EventType::Click;e.i0=((st%barLen)==0)?1:0;e.frame=frame;activate(e);}
    if(seq.audNeb){for(auto&x:seq.neb){if(x.s!=st)continue;
        Event e;e.type=EventType::Neb;e.frame=frame;e.i0=x.midi;e.i1=x.pluck?1:0;
        e.a=x.vel;e.b=std::max(.01f,x.len*stepDur);e.c=seq.nebDecay;e.n1=x.bn;e.n2=x.vn;
        for(int k=0;k<x.bn;k++){e.t1[k]=x.bt[k];e.v1[k]=x.bv[k];}
        for(int k=0;k<x.vn;k++){e.t2[k]=x.vt[k];e.v2[k]=x.vv[k];}
        activate(e);
    }}
    if(seq.audSpec){for(auto&x:seq.spec){if(x.s!=st)continue;
        float d=std::max(.01f,x.len*stepDur);
        Event e1;e1.type=EventType::Spec;e1.frame=frame;e1.i0=x.midi1;e1.i1=seq.specW1;e1.a=x.vel;e1.b=d;activate(e1);
        Event e2;e2.type=EventType::Spec;e2.frame=frame;e2.i0=x.midi2;e2.i1=seq.specW2;e2.a=x.vel;e2.b=d;activate(e2);
    }}
    if(seq.audDrums){for(auto&x:seq.drums){if(x.s!=st)continue;
        int kind=seqDrumKind(x.midi);Event e;e.type=EventType::Drum;e.frame=frame;e.i0=kind;
        e.a=seqMidiFreq(x.midi);e.b=x.vel;e.c=seqDrumPos(kind,x.vel);activate(e);
    }}
}
