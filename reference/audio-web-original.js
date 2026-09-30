export let ctx = null;
export const buses = {};
let noiseBuf = null, metalBuf = null, wavePool = [];
let nebWaves = null, nebAmps = null, nebWaveCache = null, bowBuf = null, pluckBuf = null;
let verb = null, verbGain = null, delayN = null, delayG = null, delayF = null;
let nebVerb = null, nebVerbGain = null, specVerb = null, specVerbGain = null;
let lastNebVerbDecay = -1, lastSpecVerbRoom = -1;
let nebPost = null, nebDistDry = null, nebDistPre = null, nebDistShaper = null, nebDistWet = null;
let nebDly = null, nebDlyWet = null, nebDlyFb = null;
let specPost = null, specDly = null, specDlyWet = null, specDlyFb = null;
let drumComp = null, drumVerbPre = null, drumVerbTone = null;
const stickCache = {}, cymVoices = [], CYM_MAX_VOICES = 3;
const nebDistCurveCache = new Map();
export let mediaDest = null;
export const NOTE_NAMES = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"];
export function midiName(m){ return NOTE_NAMES[((m%12)+12)%12] + (Math.floor(m/12)-1); }
export function midiFreq(m){ return 440*Math.pow(2,(m-69)/12); }
export function noteFreq(n){
  const SEMI={C:0,"C#":1,D:2,"D#":3,E:4,F:5,"F#":6,G:7,"G#":8,A:9,"A#":10,B:11};
  const name=n.slice(0,-1), oct=parseInt(n.slice(-1),10);
  return 440*Math.pow(2,(((oct+1)*12+SEMI[name])-69)/12);
}
function ext(base,total,fall){
  const v=base.slice();
  while(v.length<total){ v.push((v[v.length-1]||0.01)*fall); }
  return v;
}
function pwave(c,amps){
  const real=new Float32Array(amps.length+1), imag=new Float32Array(amps.length+1);
  for(let i=1;i<=amps.length;i++) imag[i]=amps[i-1];
  return c.createPeriodicWave(real,imag,{disableNormalization:false});
}
export function ensure(){
  if(ctx) { if(ctx.state==="suspended") ctx.resume(); return ctx; }
  ctx = new (window.AudioContext||window.webkitAudioContext)();
  const master = ctx.createGain(); master.gain.value = 0.9; master.connect(ctx.destination);
  mediaDest = ctx.createMediaStreamDestination(); master.connect(mediaDest);
  const comp = ctx.createDynamicsCompressor(); comp.threshold.value=-16; comp.ratio.value=4;
  const mus = ctx.createGain(); mus.gain.value=1; mus.connect(master);
  buses.master=master; buses.music=mus;
  for(const k of ["neb","spec","drums"]){
    const g=ctx.createGain(); g.gain.value=k==="drums"?0.9:0.8; buses[k]=g;
  }
  drumComp=ctx.createDynamicsCompressor(); drumComp.threshold.value=-14; drumComp.ratio.value=5;
  drumComp.connect(mus); buses.drums.connect(drumComp); buses.drumComp=drumComp;
  nebPost=ctx.createGain(); nebPost.connect(mus); buses.nebPost=nebPost;
  nebDistDry=ctx.createGain(); nebDistDry.gain.value=1;
  nebDistPre=ctx.createGain(); nebDistPre.gain.value=0;
  nebDistShaper=ctx.createWaveShaper(); nebDistShaper.oversample="4x";
  nebDistWet=ctx.createGain(); nebDistWet.gain.value=0;
  buses.neb.connect(nebDistDry); nebDistDry.connect(nebPost);
  buses.neb.connect(nebDistPre); nebDistPre.connect(nebDistShaper); nebDistShaper.connect(nebDistWet); nebDistWet.connect(nebPost);
  nebDly=ctx.createDelay(2.0); nebDly.delayTime.value=0.32;
  nebDlyFb=ctx.createGain(); nebDlyFb.gain.value=0;
  nebDlyWet=ctx.createGain(); nebDlyWet.gain.value=0;
  nebPost.connect(nebDly); nebDly.connect(nebDlyWet); nebDlyWet.connect(mus);
  nebDly.connect(nebDlyFb); nebDlyFb.connect(nebDly);
  specPost=ctx.createGain(); specPost.connect(mus); buses.specPost=specPost;
  buses.spec.connect(specPost);
  specDly=ctx.createDelay(2.0); specDly.delayTime.value=0.25;
  specDlyFb=ctx.createGain(); specDlyFb.gain.value=0;
  specDlyWet=ctx.createGain(); specDlyWet.gain.value=0;
  specPost.connect(specDly); specDly.connect(specDlyWet); specDlyWet.connect(mus);
  specDly.connect(specDlyFb); specDlyFb.connect(specDly);
  verb=ctx.createConvolver(); verb.buffer=roomIR(2.8);
  verbGain=ctx.createGain(); verbGain.gain.value=0.18;
  verb.connect(verbGain); verbGain.connect(mus);
  nebVerb=ctx.createConvolver(); nebVerb.buffer=nebVerbIR(2.5); lastNebVerbDecay=2.5;
  nebVerbGain=ctx.createGain(); nebVerbGain.gain.value=1;
  nebVerb.connect(nebVerbGain); nebVerbGain.connect(mus);
  specVerb=ctx.createConvolver(); specVerb.buffer=specVerbIR(0.3+0.5*2.5); lastSpecVerbRoom=0.5;
  specVerbGain=ctx.createGain(); specVerbGain.gain.value=1;
  specVerb.connect(specVerbGain); specVerbGain.connect(mus);
  for(const k of ["neb","spec","drums"]){
    const s=ctx.createGain(); s.gain.value=k==="drums"?0.5:0.35;
    (k==="neb"?nebPost:(k==="spec"?specPost:buses[k])).connect(s);
    s.connect(k==="neb"?nebVerb:(k==="spec"?specVerb:verb)); buses[k+"_verbSend"]=s;
  }
  buses.drums_verbSend.disconnect();
  drumVerbPre=ctx.createDelay(0.08); drumVerbPre.delayTime.value=0.014;
  drumVerbTone=ctx.createBiquadFilter(); drumVerbTone.type="lowpass"; drumVerbTone.frequency.value=6200; drumVerbTone.Q.value=0.35;
  buses.drums_verbSend.connect(drumVerbPre); drumVerbPre.connect(drumVerbTone); drumVerbTone.connect(verb);
  delayN=ctx.createDelay(1.2); delayN.delayTime.value=0.32;
  delayG=ctx.createGain(); delayG.gain.value=0.0;
  delayF=ctx.createGain(); delayF.gain.value=0.35;
  delayN.connect(delayF); delayF.connect(delayN); delayN.connect(delayG); delayG.connect(mus);
  const ds=ctx.createGain(); ds.gain.value=1; buses.spec.connect(ds); ds.connect(delayN);
  const dn=ctx.createGain(); dn.gain.value=1; nebPost.connect(dn); dn.connect(delayN);
  buses.delayWet=delayG;
  const len=ctx.sampleRate*2; noiseBuf=ctx.createBuffer(1,len,ctx.sampleRate);
  const d=noiseBuf.getChannelData(0); for(let i=0;i<len;i++) d[i]=Math.random()*2-1;
  const ml=ctx.sampleRate*8; metalBuf=ctx.createBuffer(1,ml,ctx.sampleRate);
  const md=metalBuf.getChannelData(0); let slow=0,prev=0;
  for(let i=0;i<ml;i++){ const w=Math.random()*2-1; slow=slow*0.965+w*0.035; const br=w-slow, ed=w-prev; prev=w; md[i]=Math.max(-1,Math.min(1,br*0.68+ed*0.17+w*0.15)); }
  for(let i=0;i<10;i++){ const p=Math.random()*Math.PI*2; const r=new Float32Array(2), im=new Float32Array(2); r[1]=Math.cos(p); im[1]=Math.sin(p); wavePool.push(ctx.createPeriodicWave(r,im,{disableNormalization:true})); }
  const open=ext([1,0.7,0.5,0.38,0.28,0.2],18,0.78);
  const pluck=ext([1,0.75,0.55,0.42,0.34,0.27,0.22],18,0.82);
  const palm=ext([1.55,1.18,0.78,0.4,0.18,0.08],18,0.46);
  nebAmps={open:open,pluck:pluck,palm:palm};
  nebWaves={open:pwave(ctx,open),pluck:pwave(ctx,pluck),palm:pwave(ctx,palm)};
  nebWaveCache=new Map([[0,nebWaves.open],[1,nebWaves.palm]]);
  const bl=Math.floor(ctx.sampleRate*0.5); bowBuf=ctx.createBuffer(1,bl,ctx.sampleRate);
  const bd=bowBuf.getChannelData(0); let last=0;
  for(let i=0;i<bl;i++){ const w=Math.random()*2-1; last=last*0.82+w*0.18; bd[i]=last*0.75; }
  const pl=Math.floor(ctx.sampleRate*0.18); pluckBuf=ctx.createBuffer(1,pl,ctx.sampleRate);
  const pd=pluckBuf.getChannelData(0); for(let i=0;i<pl;i++) pd[i]=(Math.random()*2-1)*Math.exp(-i/(pl*0.22));
  return ctx;
}
function roomIR(sec){
  const len=Math.floor(ctx.sampleRate*sec), imp=ctx.createBuffer(2,len,ctx.sampleRate);
  const early=[0.011,0.019,0.031,0.047,0.071,0.103];
  for(let ch=0;ch<2;ch++){ const dd=imp.getChannelData(ch); let sm=0;
    for(let i=0;i<len;i++){ const t=i/ctx.sampleRate, w=Math.random()*2-1; sm=sm*0.78+w*0.22;
      dd[i]=(w*0.62+sm*0.38)*Math.pow(1-t/sec,2.7)*Math.exp(-t*1.15)*Math.min(1,t*38)*0.27; }
    early.forEach((s,ix)=>{ const j=(ch?0.00073:-0.00051)*(ix+1); const at=Math.max(0,Math.floor((s+j)*ctx.sampleRate)); if(at<len) dd[at]+=(0.42/(1+ix*0.48))*(ix%2?-1:1); });
  }
  return imp;
}
export function setMasterVolume(v){ ensure(); buses.master.gain.setTargetAtTime(v,ctx.currentTime,0.02); }
export function setTrackVolume(k,v){ ensure(); if(buses[k]) buses[k].gain.setTargetAtTime(v,ctx.currentTime,0.02); }
export function setReverb(v){ ensure(); verbGain.gain.setTargetAtTime(v*0.7,ctx.currentTime,0.03); }
export function setDelayMix(v){ ensure(); delayG.gain.setTargetAtTime(v*0.6,ctx.currentTime,0.03); }
export function setDelayTime(v){ ensure(); delayN.delayTime.setTargetAtTime(v,ctx.currentTime,0.03); }
function denv(g,t,a,peak,dec){
  g.gain.setValueAtTime(0.0001,t); g.gain.linearRampToValueAtTime(Math.max(0.0001,peak),t+a); g.gain.exponentialRampToValueAtTime(0.0001,t+a+dec);
}
function dnoise(type,freq,Q,peak,dec,t,sweepTo){
  const n=ctx.createBufferSource(); n.buffer=noiseBuf;
  const f=ctx.createBiquadFilter(); f.type=type; f.frequency.setValueAtTime(freq,t); f.Q.value=Q||0.8;
  if(sweepTo) f.frequency.exponentialRampToValueAtTime(Math.max(40,sweepTo),t+dec);
  const g=ctx.createGain(); denv(g,t,0.001,peak,dec);
  n.connect(f); f.connect(g); g.connect(buses.drums); n.start(t); n.stop(t+dec+0.2);
}
function impactMode(freq,peak,dec,t,sweepFrom,type){
  const o=ctx.createOscillator(), g=ctx.createGain(); o.type=type||"sine";
  const f=Math.max(24,Math.min(ctx.sampleRate*0.42,freq));
  const startF=Math.max(24,Math.min(ctx.sampleRate*0.42,f*(sweepFrom||1)));
  o.frequency.setValueAtTime(startF,t);
  if(Math.abs(startF-f)>0.5) o.frequency.exponentialRampToValueAtTime(f,t+Math.min(0.09,Math.max(0.018,dec*0.22)));
  denv(g,t,0.001,Math.max(0.0001,peak),dec);
  o.connect(g); g.connect(buses.drums); o.start(t); o.stop(t+dec+0.12);
}
function drumModes(base,vel,body,t,edge,fundDecayMul){
  fundDecayMul=fundDecayMul==null?1:fundDecayMul;
  const ratios=[1,1.593,2.136,2.296,2.653,2.918], gains=[1,0.34,0.20,0.13,0.09,0.06];
  ratios.forEach((ratio,i)=>{
    const edgeShape=i===0?(1-0.48*edge):(0.82+edge*0.42);
    const decay=(body/(1+i*0.25))*(i===0?fundDecayMul:0.82);
    impactMode(base*ratio,vel*gains[i]*edgeShape,decay,t,i===0?1.045:1.012,"sine");
  });
}
export function drumKickAt(freq,vel,pos,t){
  ensure(); pos=Math.min(1,Math.max(0,pos??0.15)); vel=Math.min(1.15,Math.max(0.05,vel));
  const hard=1-pos, soft=pos;
  const lowBoost=Math.min(1,Math.max(0,(130-freq)/130));
  const bodyDec=0.48+vel*0.34+soft*0.15+lowBoost*0.18, fundDec=bodyDec*1.8;
  const o=ctx.createOscillator(), g=ctx.createGain(); o.type="sine";
  o.frequency.setValueAtTime(Math.max(35,freq*(1.72+0.22*hard)),t);
  o.frequency.exponentialRampToValueAtTime(Math.max(30,freq),t+0.052+soft*0.025);
  denv(g,t,0.001,vel*(1.0+soft*0.15+lowBoost*0.25),fundDec);
  o.connect(g); g.connect(buses.drums); o.start(t); o.stop(t+fundDec+0.15);
  impactMode(freq*2.0,vel*0.16,0.30+soft*0.10,t,1.02,"sine");
  impactMode(freq*1.593,vel*(0.16+0.05*hard),0.23+soft*0.08,t,1.025,"sine");
  impactMode(freq*2.136,vel*(0.085+0.055*hard),0.14+soft*0.05,t,1.018,"sine");
  impactMode(freq*2.296,vel*0.055,0.105,t,1.012,"sine");
  impactMode(Math.max(27,freq*0.50),vel*(0.32+soft*0.20),0.42+soft*0.22,t,1.04,"sine");
  dnoise("bandpass",Math.min(620,Math.max(150,freq*3.2)),0.75,vel*(0.14+soft*0.08),0.075,t);
  dnoise("bandpass",1650+hard*900,1.15,vel*(0.11+hard*0.19),0.026+soft*0.012,t);
  dnoise("highpass",4300+hard*900,0.55,vel*(0.025+hard*0.075),0.014,t);
}
function sstep(a,b,x){ const t=Math.min(1,Math.max(0,(x-a)/(b-a))); return t*t*(3-2*t); }
export function drumSnareAt(freq,vel,r,t){
  ensure(); r=Math.min(1,Math.max(0,r||0)); vel=Math.min(1.15,Math.max(0.05,vel));
  const edge=sstep(0.28,0.94,r), center=1-edge;
  drumModes(freq,vel*(0.52+center*0.20),0.29+vel*0.15,t,edge,1.8);
  dnoise("bandpass",1050+edge*950,1.0,vel*(0.12+edge*0.16),0.026,t);
  dnoise("bandpass",2850+edge*850,0.9,vel*(0.15+edge*0.12),0.045,t);
  const wire=vel*(0.52+0.16*center+0.20*edge);
  dnoise("bandpass",1750,0.62,wire*0.72,0.16+center*0.08,t,1250);
  dnoise("bandpass",3550,0.78,wire*0.62,0.12+center*0.07,t,2400);
  dnoise("highpass",6500,0.52,wire*0.34,0.09+center*0.08,t);
  dnoise("bandpass",720,1.4,vel*0.10*(0.6+center),0.11,t);
  if(edge>0.12){
    impactMode(freq*3.74,vel*0.12*edge,0.065,t,1.012,"sine");
    impactMode(freq*5.43,vel*0.075*edge,0.045,t,1.008,"sine");
    dnoise("bandpass",4700,2.2,vel*0.22*edge,0.038,t);
  }
}
function cymW(){ if(wavePool.length) return wavePool[(Math.random()*wavePool.length)|0]; const p=Math.random()*Math.PI*2; const r=new Float32Array(2),im=new Float32Array(2); r[1]=Math.cos(p); im[1]=Math.sin(p); return ctx.createPeriodicWave(r,im,{disableNormalization:true}); }
function metalPartial(freq,peak,dec,t,spread,V,stablePitch,motionAmt){
  spread=spread==null?0.007:spread;
  motionAmt=motionAmt==null?1:Math.min(1,Math.max(0,motionAmt));
  const o=ctx.createOscillator(), g=ctx.createGain(); o.setPeriodicWave(cymW());
  const base=Math.max(70,Math.min(ctx.sampleRate*0.43,freq));
  if(stablePitch){ o.frequency.setValueAtTime(base,t); }
  else{
    const jitter=(Math.random()*2-1)*spread*0.24*motionAmt;
    const f=Math.max(70,Math.min(ctx.sampleRate*0.43,base*(1+jitter)));
    const drift=1+(0.0015+Math.random()*0.0035)*motionAmt;
    o.frequency.setValueAtTime(f*drift,t);
    o.frequency.exponentialRampToValueAtTime(f,t+Math.min(0.12,Math.max(0.025,dec*0.18)));
  }
  denv(g,t,0.0006,Math.max(0.0001,peak),dec);
  o.connect(g); g.connect(V?V.bus:buses.drums); o.start(t); o.stop(t+dec+0.12);
  if(V) V.srcs.push(o);
}
function cymNoiseBand(freq,Q,peak,dec,t,sweepTo,flutter,V){
  if(peak<=0.0001||dec<=0.01) return;
  const buf=metalBuf||noiseBuf;
  const n=ctx.createBufferSource(); n.buffer=buf; n.playbackRate.value=0.96+Math.random()*0.08;
  const f=ctx.createBiquadFilter(); f.type="bandpass";
  const startF=Math.max(180,Math.min(ctx.sampleRate*0.42,freq*(0.96+Math.random()*0.08)));
  f.frequency.setValueAtTime(startF,t); f.Q.value=Q||0.7;
  if(sweepTo){ const target=Math.max(160,Math.min(ctx.sampleRate*0.42,sweepTo)); f.frequency.exponentialRampToValueAtTime(target,t+dec); }
  const eg=ctx.createGain(); denv(eg,t,0.0015,peak,dec);
  const motion=ctx.createGain(); motion.gain.setValueAtTime(1,t);
  n.connect(f); f.connect(eg); eg.connect(motion); motion.connect(V?V.bus:buses.drums);
  let lfo=null, lg=null;
  if(flutter&&flutter>0){
    if(V&&V.lfo){ lg=ctx.createGain(); lg.gain.value=Math.min(0.16,flutter); V.lfo.connect(lg); lg.connect(motion.gain); }
    else{ lfo=ctx.createOscillator(); lg=ctx.createGain(); lfo.type="sine"; lfo.frequency.value=3.4+Math.random()*8.6; lg.gain.value=Math.min(0.16,flutter); lfo.connect(lg); lg.connect(motion.gain); lfo.start(t); lfo.stop(t+dec+0.08); if(V&&lfo) V.srcs.push(lfo); }
  }
  const dur=Math.min(dec+0.12,Math.max(0.05,buf.duration-0.02));
  const maxOff=Math.max(0,buf.duration-dur-0.01);
  n.start(t,maxOff>0?Math.random()*maxOff:0,dur);
  if(V) V.srcs.push(n);
}
function cymImpact(vel,w,brightness,V,t){
  brightness=brightness==null?1:brightness;
  cymNoiseBand(2500+brightness*500,1.0,vel*0.13*w,0.018,t,1900+brightness*350,0,V);
  cymNoiseBand(7500+brightness*1100,0.8,vel*0.09*w,0.014,t,5800+brightness*850,0,V);
}
function renderStickContact(bellness){
  const dur=0.075+bellness*0.075;
  const frames=Math.max(64,Math.floor(ctx.sampleRate*dur));
  const b=ctx.createBuffer(1,frames,ctx.sampleRate);
  const d=b.getChannelData(0);
  const modes=[2350,3180,4210,5480,7060,9050,11300];
  const phases=modes.map(()=>Math.random()*Math.PI*2);
  const freqs=modes.map((f,i)=>f*(0.94+Math.random()*0.12)*(1+(i%2?bellness*0.018:0)));
  const taus=modes.map((f,i)=>0.010+i*0.0045+bellness*(i<4?0.020:0.008));
  let slow=0;
  for(let i=0;i<frames;i++){
    const tt=i/ctx.sampleRate;
    const contactEnv=Math.exp(-tt*145);
    const white=Math.random()*2-1;
    slow=slow*0.72+white*0.28;
    const crack=(white-slow)*contactEnv*0.52;
    let metal=0;
    for(let k=0;k<freqs.length;k++){
      const wobDepth=0.0025*(1-0.9*bellness);
      const wob=1+wobDepth*Math.sin(2*Math.PI*(17+k*3.7)*tt+phases[k]*0.37);
      metal+=Math.sin(2*Math.PI*freqs[k]*wob*tt+phases[k])*Math.exp(-tt/taus[k])*(0.18/(1+k*0.17));
    }
    const rough=0.86+Math.random()*0.28;
    d[i]=Math.max(-1,Math.min(1,(crack+metal*rough)*0.82));
  }
  return b;
}
function cymStickContact(vel,amount,bellness,V,t){
  if(amount<=0.004) return;
  bellness=Math.min(1,Math.max(0,bellness||0));
  const key=Math.round(bellness*2);
  const b=stickCache[key]||(stickCache[key]=renderStickContact(key/2));
  const bd=key/2, dur=0.075+bd*0.075;
  const src=ctx.createBufferSource(); src.buffer=b;
  const rateSpread=0.03*(1-0.9*bellness);
  src.playbackRate.value=1+(Math.random()*2-1)*rateSpread;
  const hp=ctx.createBiquadFilter(); hp.type="highpass"; hp.frequency.value=1450; hp.Q.value=0.55;
  const peak=ctx.createBiquadFilter(); peak.type="peaking"; peak.frequency.value=4200+bellness*450; peak.Q.value=0.8; peak.gain.value=2.5+bellness*1.5;
  const g=ctx.createGain(); g.gain.value=amount*vel;
  src.connect(hp); hp.connect(peak); peak.connect(g); g.connect(V?V.bus:buses.drums);
  src.start(t); src.stop(t+dur+0.01);
  if(V) V.srcs.push(src);
}
const CYM_STICK_LEVEL=0.28;
function cymStickTing(anchor,vel,amount,bellness,V,t){
  amount*=CYM_STICK_LEVEL;
  if(amount<=0.005) return;
  bellness=Math.min(1,Math.max(0,bellness||0));
  cymImpact(vel,amount,0.92+bellness*0.45,V,t);
  cymStickContact(vel,amount,bellness,V,t);
  if(bellness>0.18){
    const amt=amount*vel*bellness;
    cymNoiseBand(3200,4.5,amt*0.06,0.07+bellness*0.05,t,2800,0.015,V);
  }
}
function cymWash(level,dec,brightness,V,t){
  if(level<=0.003) return;
  brightness=Math.min(1.4,Math.max(0.5,brightness||1));
  cymNoiseBand(2350*brightness,0.62,level*0.43,dec*0.92,t,1850*brightness,0.055,V);
  cymNoiseBand(4100*brightness,0.72,level*0.37,dec*0.68,t,3000*brightness,0.075,V);
  cymNoiseBand(6900*brightness,0.80,level*0.27,dec*0.43,t,4700*brightness,0.095,V);
  cymNoiseBand(10500*brightness,0.68,level*0.15,dec*0.22,t,7200*brightness,0.12,V);
}
function cymVoice(life,t){
  ensure();
  const bus=ctx.createGain(); bus.gain.value=0.7; bus.connect(buses.drums);
  const lfo=ctx.createOscillator(); lfo.type="sine"; lfo.frequency.value=3.4+Math.random()*8.6;
  const stopAt=Math.min(6.5,(life||2)+0.4);
  lfo.start(t); lfo.stop(t+stopAt);
  const V={bus:bus,lfo:lfo,srcs:[lfo]};
  cymVoices.push(V);
  if(cymVoices.length>CYM_MAX_VOICES){
    const old=cymVoices.shift();
    try{ old.bus.gain.setTargetAtTime(0.0001,t,0.015); }catch(e){}
    old.srcs.forEach(s=>{ try{ s.stop(t+0.12); }catch(e){} });
    setTimeout(()=>{ try{ old.bus.disconnect(); }catch(e){} },400);
  }
  return V;
}
export function drumCymbalAt(freq,vel,r,t){
  ensure(); r=Math.min(1,Math.max(0,r??0.9)); vel=Math.min(1.15,Math.max(0.05,vel));
  const wBell=1-sstep(0.24,0.49,r), wEdge=sstep(0.61,0.9,r), wBow=Math.max(0,1-wBell-wEdge);
  const anchor=freq;
  let maxLife=0;
  if(wBell>0.01) maxLife=Math.max(maxLife,1.15+vel*0.75);
  if(wBow>0.01) maxLife=Math.max(maxLife,1.45+vel*0.9);
  if(wEdge>0.01) maxLife=Math.max(maxLife,2.2+vel*1.0+sstep(0.62,1,r)*0.7);
  const V=cymVoice(maxLife,t);
  if(wBell>0.01){
    const bodyDec=1.15+vel*0.75;
    cymStickTing(anchor,vel,wBell*1.15,1,V,t);
    if(vel*wBell>0.01) metalPartial(anchor,vel*wBell*0.34,bodyDec,t,0,V,true);
    [1.71,2.74,4.16].forEach((m,i)=>{
      const amp=vel*wBell*0.055/(1+i*0.5), life=Math.max(0.14,bodyDec*Math.pow(0.6,i+1));
      if(amp>0.003) metalPartial(anchor*m,amp,life,t,0.005+i*0.0008,V,false,0.08);
    });
    cymWash(vel*wBell*0.10,0.72+vel*0.32,1.08,V,t);
  }
  if(wBow>0.01){
    const bodyDec=1.45+vel*0.9;
    cymStickTing(anchor,vel,wBow*0.9,0.42,V,t);
    if(vel*wBow>0.01) metalPartial(anchor,vel*wBow*0.20,bodyDec,t,0,V,true);
    [1.82,2.74,3.82,5.2].forEach((m,i)=>{
      const amp=vel*wBow*0.038/(1+i*0.5), life=Math.max(0.13,bodyDec*Math.pow(0.58,i+1));
      if(amp>0.0028) metalPartial(anchor*m,amp,life,t,0.007+i*0.0008,V);
    });
    cymWash(vel*wBow*0.30,bodyDec,1.0,V,t);
  }
  if(wEdge>0.01){
    const k=sstep(0.62,1,r), bodyDec=2.2+vel*1.0+k*0.7;
    cymStickTing(anchor,vel,wEdge*0.56,0.12,V,t);
    if(vel*wEdge>0.01) metalPartial(anchor,vel*wEdge*0.14,bodyDec,t,0,V,true);
    [1.76,2.54,3.62,4.9,6.4].forEach((m,i)=>{
      const amp=vel*wEdge*0.028/(1+i*0.5), life=Math.max(0.14,bodyDec*Math.pow(0.58,i+1));
      if(amp>0.0025) metalPartial(anchor*m,amp,life,t,0.009+i*0.001,V);
    });
    cymWash(vel*wEdge*0.45,bodyDec,0.94+k*0.08,V,t);
    metalPartial(anchor*2.0,vel*wEdge*0.038,bodyDec*0.72,t,0,V,true);
  }
}
const VEL_LO=0.05, VEL_HI=1.15;
const VEL_B1=VEL_LO+(VEL_HI-VEL_LO)/3, VEL_B2=VEL_LO+2*(VEL_HI-VEL_LO)/3;
function velClamp(vel){ return Math.min(VEL_HI,Math.max(VEL_LO,vel??1)); }
export function drumZoneIdx(vel){
  const v=velClamp(vel);
  return v<VEL_B1?0:v<VEL_B2?1:2;
}
function drumZoneX(vel,anchors){
  const v=velClamp(vel);
  const c0=(VEL_LO+VEL_B1)/2, c1=(VEL_B1+VEL_B2)/2, c2=(VEL_B2+VEL_HI)/2;
  let p;
  if(v<=c0) p=anchors[0];
  else if(v<=c1) p=anchors[0]+(anchors[1]-anchors[0])*(v-c0)/(c1-c0);
  else if(v<=c2) p=anchors[1]+(anchors[2]-anchors[1])*(v-c1)/(c2-c1);
  else p=anchors[2];
  const lo=Math.min(anchors[0],anchors[2]), hi=Math.max(anchors[0],anchors[2]);
  return Math.min(hi,Math.max(lo,p));
}
export function nebMuteMix(pluck,vel){
  if(!pluck) return 0;
  const v=velClamp(vel);
  if(v<=VEL_B1) return 1;
  if(v>=VEL_B2) return 0;
  return (VEL_B2-v)/(VEL_B2-VEL_B1);
}
function nebWaveForMuteMix(mm){
  ensure();
  const q=Math.round(Math.min(1,Math.max(0,mm))*16)/16;
  if(nebWaveCache.has(q)) return nebWaveCache.get(q);
  const amps=nebAmps.open.map((a,i)=>(a*(1-q))+(nebAmps.palm[i]??0)*q);
  const w=pwave(ctx,amps);
  nebWaveCache.set(q,w);
  return w;
}
export function drumZone(kind,vel){
  if(kind==="c") return drumZoneX(vel,[0.08,0.5,0.97]);
  if(kind==="s") return drumZoneX(vel,[0.9,0.5,0.08]);
  return drumZoneX(vel,[0.85,0.4,0.06]);
}
export function nebNoteAt(midi,vel,durBeats,stepDur,t,pluck,decayMs,bend,vex){
  ensure();
  vel=Math.min(1.15,Math.max(0.05,vel??0.95));
  const muteMix=nebMuteMix(pluck,vel);
  const palm=muteMix>=1;
  const trim=vel>1?vel:1;
  const pressure=pluck?(palm?1:Math.min(1,Math.max(0,vel))):Math.min(1,Math.max(0,(vel-VEL_LO)/(VEL_HI-VEL_LO)));
  const level=pluck?(palm?1:Math.min(1,Math.max(0,vel))*(1-muteMix)+muteMix):0.06+Math.pow(pressure,2.35)*0.94;
  const mult=(!pluck&&!muteMix)?0.95+0.12*pressure:1+0.26*muteMix;
  const onset=Math.max(0.0001,(0.03+0.21*level)*mult*trim);
  const f=midiFreq(midi), dur=durBeats*stepDur;
  const rel=Math.max(0.05,(decayMs||700)/1000);
  const osc=ctx.createOscillator(); osc.setPeriodicWave(nebWaveForMuteMix(muteMix)); osc.frequency.value=f;
  if(bend&&bend.length){
    const pts=bend.filter(p=>p&&isFinite(p.t)&&isFinite(p.st)).map(p=>({t:Math.min(1,Math.max(0,p.t)),st:Math.min(12,Math.max(-12,p.st))})).sort((a,b)=>a.t-b.t);
    for(const p of pts){
      const pf=Math.max(20,f*Math.pow(2,p.st/12));
      if(p.t<=0.001) osc.frequency.setValueAtTime(pf,t);
      else osc.frequency.linearRampToValueAtTime(pf,t+p.t*dur);
    }
  }
  const bt=pluck
    ? {c:Math.min(2200,Math.max(120,f*(4.0-2.3*muteMix))),q:1.0+5.4*muteMix,g:5.8+7.8*muteMix,s:(5200*(1-muteMix))+(1350*muteMix)}
    : {c:Math.min(3200,Math.max(160,f*(3+1.5*pressure))),q:0.8+1.8*pressure,g:4+4.8*pressure,s:1500+4200*pressure};
  const body=ctx.createBiquadFilter(); body.type="peaking";
  body.frequency.value=bt.c; body.Q.value=bt.q; body.gain.value=bt.g;
  const sm=ctx.createBiquadFilter(); sm.type="lowpass"; sm.frequency.value=bt.s; sm.Q.value=0.2;
  const g=ctx.createGain();
  osc.connect(body); body.connect(sm); sm.connect(g);
  const vv=(vex&&vex.length)?vex.filter(p=>p&&isFinite(p.t)&&isFinite(p.v)).map(p=>({t:Math.min(1,Math.max(0,p.t)),v:Math.min(1.15,Math.max(0,p.v))})).sort((a,b)=>a.t-b.t):null;
  const refVel=vv?Math.min(1.15,Math.max(vel,...vv.map(p=>p.v))):vel;
  if(vv){
    const gx=ctx.createGain();
    const vAt=tt=>{ if(tt<=vv[0].t) return vv[0].v; for(let i=1;i<vv.length;i++){ if(tt<=vv[i].t){ const a=vv[i-1],b=vv[i],k=(tt-a.t)/Math.max(1e-6,b.t-a.t); return a.v+(b.v-a.v)*k; } } return vv[vv.length-1].v; };
    const R=v=>Math.max(0.0001,Math.min(1.25,v/Math.max(0.05,refVel)));
    gx.gain.setValueAtTime(R(vAt(0)),t);
    for(const p of vv) gx.gain.linearRampToValueAtTime(R(p.v),t+p.t*dur);
    gx.gain.setValueAtTime(R(vv[vv.length-1].v),t+dur);
    g.connect(gx); gx.connect(buses.neb);
  } else g.connect(buses.neb);
  if(pluck){
    const startGain=Math.max(0.0001,(0.03+0.21*level)*(1+0.26*muteMix)*trim);
    const decayScale=muteMix>0?Math.max(0.08,1-0.92*muteMix):(1-0.32*muteMix);
    const decEnd=t+Math.max(0.003,rel*decayScale);
    g.gain.setValueAtTime(startGain,t);
    g.gain.exponentialRampToValueAtTime(0.0001,decEnd);
    const trOsc=ctx.createOscillator(); trOsc.setPeriodicWave(nebWaves.pluck);
    trOsc.frequency.value=f*(1.95-0.95*muteMix);
    const trF=ctx.createBiquadFilter(); trF.type="bandpass";
    trF.frequency.value=(1800*(1-muteMix))+(520*muteMix); trF.Q.value=0.9+(2.5*muteMix);
    const trG=ctx.createGain();
    trG.gain.setValueAtTime(Math.max(0.0001,(0.03+0.21*level)*(0.40+0.30*muteMix)*trim),t);
    trG.gain.exponentialRampToValueAtTime(0.0001,t+(0.05-0.01*muteMix));
    trOsc.connect(trF); trF.connect(trG); trG.connect(g);
    trOsc.start(t); trOsc.stop(t+0.085);
    const ns=ctx.createBufferSource(); ns.buffer=pluckBuf;
    const nf=ctx.createBiquadFilter(); nf.type="bandpass";
    nf.frequency.value=(3000*(1-muteMix))+(1200*muteMix); nf.Q.value=0.7+(1.3*muteMix);
    const ng=ctx.createGain();
    ng.gain.setValueAtTime(Math.max(0.0001,(0.035+0.02*muteMix)*trim),t);
    ng.gain.exponentialRampToValueAtTime(0.0001,t+(0.028+0.008*muteMix));
    ns.connect(nf); nf.connect(ng); ng.connect(g); ns.start(t); ns.stop(t+0.25);
    osc.start(t); osc.stop(decEnd+0.15);
  } else {
    g.gain.setValueAtTime(0.0001,t);
    g.gain.linearRampToValueAtTime(Math.max(0.0001,onset*(0.68+pressure*0.10)),t+0.024);
    g.gain.linearRampToValueAtTime(onset,t+0.085);
    const hold=Math.max(0.085,dur);
    g.gain.setValueAtTime(onset,t+hold);
    g.gain.exponentialRampToValueAtTime(0.0001,t+hold+rel);
    const ns=ctx.createBufferSource(); ns.buffer=bowBuf; ns.loop=true;
    const nf=ctx.createBiquadFilter(); nf.type="bandpass"; nf.frequency.value=700+pressure*2100; nf.Q.value=0.5+pressure*1.3;
    const ng=ctx.createGain(); ng.gain.value=0.0001;
    ng.gain.linearRampToValueAtTime(Math.max(0.0001,0.004+(1-pressure)*0.012+pressure*0.0065),t+0.003);
    const ag=ctx.createGain();
    ag.gain.setValueAtTime(Math.max(0.0001,0.010+pressure*0.009),t);
    ag.gain.exponentialRampToValueAtTime(0.0001,t+0.07);
    ns.connect(nf); nf.connect(ng); ng.connect(g); nf.connect(ag); ag.connect(g);
    ns.start(t); ns.stop(t+hold+rel+0.3);
    osc.start(t); osc.stop(t+hold+rel+0.15);
  }
}
export function specOscAt(midi,vel,durBeats,stepDur,t,w){
  ensure();
  vel=Math.min(1.15,Math.max(0.05,vel??0.95));
  const dur=durBeats*stepDur, vol=Math.max(0.0001,0.1*vel);
  const o=ctx.createOscillator(); o.type=w||"square"; o.frequency.value=midiFreq(midi);
  const g=ctx.createGain(); g.gain.setValueAtTime(vol,t);
  g.gain.setValueAtTime(vol,t+Math.max(0.001,dur));
  g.gain.exponentialRampToValueAtTime(0.001,t+Math.max(0.001,dur)+0.1);
  o.connect(g); g.connect(buses.spec); g.connect(verb); g.connect(delayN);
  o.start(t); o.stop(t+dur+0.2);
}
function nebDistCurve(amount){
  const q=Math.round(Math.max(0,amount)*100)/100, key=q.toFixed(2);
  if(nebDistCurveCache.has(key)) return nebDistCurveCache.get(key);
  const samples=22050, curve=new Float32Array(samples);
  const k=q<=0?0:q*2.2;
  for(let i=0;i<samples;i++){ const x=(i*2/(samples-1))-1; curve[i]=k===0?x:((1+k)*x)/(1+k*Math.abs(x)); }
  nebDistCurveCache.set(key,curve);
  return curve;
}
function nebVerbIR(sec){
  const dur=Math.max(0.1,sec), len=Math.max(1,Math.floor(ctx.sampleRate*dur));
  const imp=ctx.createBuffer(2,len,ctx.sampleRate);
  for(let ch=0;ch<2;ch++){ const dd=imp.getChannelData(ch);
    for(let i=0;i<len;i++){ const t=i/len;
      dd[i]=(Math.random()*2-1)*Math.pow(1-t,dur*1.35); } }
  return imp;
}
function specVerbIR(sec){
  const len=Math.max(1,Math.floor(ctx.sampleRate*Math.max(0.1,sec)));
  const buf=ctx.createBuffer(2,len,ctx.sampleRate);
  for(let ch=0;ch<2;ch++){ const dd=buf.getChannelData(ch);
    for(let i=0;i<len;i++) dd[i]=(Math.random()*2-1)*(1-i/len); }
  return buf;
}
export function applyNebFX(fx){
  ensure();
  if(!nebPost) return;
  const t=ctx.currentTime;
  nebDistDry.gain.setTargetAtTime(fx.dstOn?0:1,t,0.02);
  nebDistPre.gain.setTargetAtTime(fx.dstOn?(fx.dstIn??1):0,t,0.02);
  nebDistWet.gain.setTargetAtTime(fx.dstOn?(fx.dstOut??0.85):0,t,0.02);
  nebDistShaper.curve=fx.dstOn?nebDistCurve(fx.dstAmt??25):null;
  nebDlyWet.gain.setTargetAtTime(fx.dlyOn?(fx.dlyAmt??0.25):0,t,0.03);
  nebDlyFb.gain.setTargetAtTime(fx.dlyOn?(fx.dlyFb??0.35):0,t,0.03);
  nebDly.delayTime.setTargetAtTime(Math.min(1.5,Math.max(0.05,fx.dlyTime??0.32)),t,0.03);
  const dec=Math.min(8,Math.max(0.3,fx.verbDecay??2.5));
  if(fx.verbOn&&Math.abs(dec-lastNebVerbDecay)>0.05){
    nebVerb.buffer=nebVerbIR(dec); lastNebVerbDecay=dec;
  }
  if(buses.neb_verbSend) buses.neb_verbSend.gain.setTargetAtTime(fx.verbOn?(fx.verbAmt??0.25):0.35,t,0.03);
}
export function applySpecFX(fx){
  ensure();
  if(!specPost) return;
  const t=ctx.currentTime;
  specDlyWet.gain.setTargetAtTime(fx.dlyOn?(fx.dlyAmt??0.4):0,t,0.03);
  specDlyFb.gain.setTargetAtTime(fx.dlyOn?(fx.dlyFb??0.3):0,t,0.03);
  specDly.delayTime.setTargetAtTime(Math.min(1.5,Math.max(0.05,fx.dlyTime??0.25)),t,0.03);
  const room=Math.min(1,Math.max(0.1,fx.verbRoom??0.5));
  if(fx.verbOn&&Math.abs(room-lastSpecVerbRoom)>0.01){
    specVerb.buffer=specVerbIR(0.3+room*2.5); lastSpecVerbRoom=room;
  }
  if(buses.spec_verbSend) buses.spec_verbSend.gain.setTargetAtTime(fx.verbOn?(fx.verbAmt??0.3):0.35,t,0.03);
}
export function applyDrumFX(fx){
  ensure();
  if(buses.drums_verbSend) buses.drums_verbSend.gain.setTargetAtTime(fx.verbOn?(fx.verbAmt??0.18):0.5,ctx.currentTime,0.03);
}
export function clickAt(t,hi){
  ensure();
  const o=ctx.createOscillator(), g=ctx.createGain();
  o.frequency.value=hi?2000:1400; denv(g,t,0.001,0.25,0.05);
  o.connect(g); g.connect(buses.master); o.start(t); o.stop(t+0.1);
}
