'use strict';
const $=id=>document.getElementById(id);
const sections=['auth','home','studio','results','history'];
let csrf,me,activeVideo,attempt,selectedFile,resumeVideo,blobUrl,yt,ytPromise,pollTimer,clockTimer;
let shown=new Set(),selections=new Map(),lateness=[],pollGeneration=0;
let playerGeneration=0,analysisBusy=false,pollFailures=0;
function page(name){for(const id of sections)$(id).hidden=id!==name;$('alert').hidden=true;window.scrollTo({top:0,behavior:'auto'});}
function error(message){$('alert').textContent=message;$('alert').hidden=false;}
function formatTime(n){n=Math.max(0,Math.floor(n||0));return `${Math.floor(n/60).toString().padStart(2,'0')}:${(n%60).toString().padStart(2,'0')}`;}
function element(tag,text,cls){const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(cls)e.className=cls;return e;}
async function token(){const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),4000);try{const r=await fetch('/api/csrf',{credentials:'same-origin',cache:'no-store',signal:controller.signal});if(!r.ok)throw Error('Cannot establish a secure session.');csrf=await r.json();}catch(e){if(e.name==='AbortError')throw Error('Cannot establish a session: the server has not responded within 4 seconds.');throw e;}finally{clearTimeout(timer);}}
async function api(path,{method='GET',body,raw=false,timeout=60000}={}){
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),timeout);
  try{
    const headers={};if(method!=='GET'&&csrf)headers[csrf.header]=csrf.token;
    if(body!==undefined)headers['Content-Type']=raw?'application/octet-stream':'application/json';
    const r=await fetch('/api'+path,{method,headers,body:body===undefined?undefined:raw?body:JSON.stringify(body),signal:controller.signal,credentials:'same-origin',cache:'no-store'});
    if(r.status===204)return null;
    const result=await r.json();
    if(!r.ok){if(r.status===401&&me){me=null;stopPolling();page('auth');$('logout').hidden=true;$('userLabel').textContent='';}throw Error(result.message||`Request failed (${r.status}).`);}
    return result;
  }catch(e){if(e.name==='AbortError')throw Error('The server has not responded within 60 seconds. Check your connection; saved upload chunks and answers are retained.');throw e;}
  finally{clearTimeout(timer);}
}
async function signIn(create){
  if(!$('authForm').reportValidity())return;
  const buttons=[...$('authForm').querySelectorAll('button')];buttons.forEach(b=>b.disabled=true);
  try{
    await token();const username=$('username').value,password=$('password').value;
    if(create)await api('/register',{method:'POST',body:{username,password}});
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),4000);
    let r;try{r=await fetch('/api/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded',[csrf.header]:csrf.token},body:new URLSearchParams({username,password}),credentials:'same-origin',signal:controller.signal});}finally{clearTimeout(timer);}
    if(!r.ok){const body=await r.json();throw Error(body.message||'Could not sign in.');}
    $('password').value='';await token();me=await api('/me');signedIn();
  }catch(e){error(e.name==='AbortError'?'Sign-in timed out. Check your connection and retry.':e.message);}finally{buttons.forEach(b=>b.disabled=false);}
}
function signedIn(){$('userLabel').textContent=me.username;$('logout').hidden=false;page('home');if(!me.aiConfigured)error('The server needs GEMINI_API_KEY before analysis can run. Your uploaded files can still be saved.');}
$('authForm').addEventListener('submit',e=>{e.preventDefault();signIn(false);});$('register').onclick=()=>signIn(true);
$('logout').onclick=async()=>{try{await api('/logout',{method:'POST'});stopPolling();resetPlayer();attempt=null;activeVideo=null;selectedFile=null;resumeVideo=null;me=null;$('file').value='';$('url').value='';$('userLabel').textContent='';$('logout').hidden=true;page('auth');await token();}catch(e){error(e.message);}};
function home(){if(!me)return page('auth');stopPolling();pausePlayer();resumeVideo=null;page('home');}
for(const id of ['homeNav','backHome','historyNew'])$(id).onclick=home;
$('historyNav').onclick=history;
document.querySelector('.brand').onclick=e=>{e.preventDefault();home();};
$('file').onchange=()=>{selectedFile=$('file').files[0];if(selectedFile){$('fileName').textContent=selectedFile.name;$('url').value='';}};
$('url').oninput=()=>{selectedFile=null;resumeVideo=null;$('file').value='';$('fileName').textContent='Choose a video file';};
function youtubeId(value){try{const u=new URL(value);if(u.protocol!=='https:'||u.username||u.password||u.port||u.hash)return null;let id;if(u.hostname==='youtu.be')id=u.pathname.slice(1);else if(['youtube.com','www.youtube.com','m.youtube.com'].includes(u.hostname)){if(u.pathname==='/watch'&&u.searchParams.getAll('v').length===1)id=u.searchParams.get('v');else if(/^\/(shorts|embed)\/[A-Za-z0-9_-]{11}$/.test(u.pathname))id=u.pathname.split('/').pop();}return /^[A-Za-z0-9_-]{11}$/.test(id||'')?id:null;}catch{return null;}}
function resetPlayer(){playerGeneration++;clearInterval(clockTimer);if(yt){yt.destroy();yt=null;}if(!$('youtube')){const host=element('div');host.id='youtube';document.querySelector('.player-shell').append(host);}if(blobUrl){URL.revokeObjectURL(blobUrl);blobUrl=null;}$('video').pause();$('video').removeAttribute('src');$('video').load();$('video').hidden=true;$('youtube').hidden=true;$('playerEmpty').hidden=false;}
function pausePlayer(){try{if(yt)yt.pauseVideo();else $('video').pause();}catch{}}
function currentTime(){try{return yt?yt.getCurrentTime():$('video').currentTime;}catch{return 0;}}
function seek(t){try{if(yt){yt.seekTo(t,true);yt.playVideo();}else{$('video').currentTime=t;$('video').play().catch(()=>{$('playbackMessage').textContent='Press Play in the video controls to continue.';});}}catch{}}
function loadYouTube(){if(window.YT?.Player)return Promise.resolve();if(ytPromise)return ytPromise;ytPromise=new Promise((resolve,reject)=>{const timeout=setTimeout(()=>{ytPromise=null;reject(Error('YouTube has not responded for over 10 seconds. The service may be unreachable; retry opening the video.'));},10500);window.onYouTubeIframeAPIReady=()=>{clearTimeout(timeout);resolve();};const s=document.createElement('script');s.src='https://www.youtube.com/iframe_api';s.onerror=()=>{clearTimeout(timeout);ytPromise=null;reject(Error('YouTube could not be reached. Check your connection.'));};document.head.append(s);});return ytPromise;}
async function youtubeDuration(){
  const generation=playerGeneration,deadline=Date.now()+20000;
  while(Date.now()<deadline){
    if(generation!==playerGeneration)throw Error('Video changed. Open the video again.');
    const duration=yt?.getDuration?.();
    if(Number.isFinite(duration)&&duration>0){if(duration>1800)throw Error('Choose a recorded YouTube video up to 30 minutes.');return duration;}
    await new Promise(resolve=>setTimeout(resolve,200));
  }
  throw Error('YouTube duration is not available. Press Play, wait for the video to load, then Retry analysis. Private, blocked and live videos are unsupported.');
}
async function player(video,localFile){
  resetPlayer();const generation=playerGeneration;clockTimer=setInterval(tick,100);$('playbackMessage').textContent='';$('playerEmpty').hidden=true;
  if(video.kind==='LOCAL'){
    const node=$('video');node.hidden=false;node.src=localFile?(blobUrl=URL.createObjectURL(localFile)):`/api/videos/${video.id}/media`;
    node.muted=true;node.play().catch(()=>{$('playbackMessage').textContent='Press Play to start the video.';});
    $('playbackMessage').textContent='Playback starts muted. Use the video controls to enable sound.';
  }else{
    $('youtube').hidden=false;await loadYouTube();if(generation!==playerGeneration)throw Error('Video changed.');
    await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('YouTube playback has not become ready for over 10 seconds. The video may be blocked or the service unreachable.')),10500);
      yt=new YT.Player('youtube',{videoId:video.youtubeId,host:'https://www.youtube-nocookie.com',playerVars:{playsinline:1,origin:location.origin,rel:0},events:{onReady:()=>{clearTimeout(timer);yt.mute();yt.playVideo();$('playbackMessage').textContent='Playback starts muted. Use the player controls to enable sound.';resolve();},onError:e=>{clearTimeout(timer);const message=`YouTube cannot play this video (code ${e.data}). It may be private, removed or embedding may be disabled. Try another public video.`;error(message);reject(Error(message));}}});
    });
    await youtubeDuration();
  }
}
$('video').addEventListener('error',()=>{if($('video').getAttribute('src'))error('This video could not be played. Use a valid H.264 MP4 with AAC audio, and check that you are signed in.');});
function status(video){activeVideo=video;$('videoTitle').textContent=video.title;$('stage').textContent=video.message;$('statusDetail').textContent=video.notice||'Your video and completed upload chunks are stored privately in your account.';$('retry').hidden=!['FAILED','UPLOADED'].includes(video.status);$('uploadProgress').hidden=video.status==='READY'||video.status==='FAILED';if(!attempt)$('quizCount').textContent=video.status==='FAILED'?'Analysis failed':video.status==='READY'?'Quiz ready':video.status==='PROCESSING'?'Analyzing':'Ready to analyze';if(video.status==='FAILED')$('waiting').textContent='Your video is saved. Retry analysis to try again.';if(video.status==='PROCESSING')$('uploadProgress').removeAttribute('value');}
function stopPolling(){clearTimeout(pollTimer);pollGeneration++;}
async function poll(id,generation){
  if(generation!==pollGeneration)return;
  try{const v=await api('/videos/'+id);if(generation!==pollGeneration)return;pollFailures=0;status(v);if(v.status==='READY'){await beginAttempt();return;}if(v.status==='FAILED'||v.status==='UPLOADED')return;}
  catch(e){if(generation===pollGeneration){error(e.message);if(++pollFailures>=6){$('stage').textContent='Connection lost';$('waiting').textContent='Your video is retained. Retry analysis to reconnect, or reopen it from My learning.';$('quizCount').textContent='Disconnected';$('retry').hidden=false;return;}}}
  if(generation===pollGeneration)pollTimer=setTimeout(()=>poll(id,generation),1000);
}
function watchJob(id){stopPolling();pollFailures=0;poll(id,pollGeneration);}
async function fingerprint(file){const hash=await crypto.subtle.digest('SHA-256',await file.arrayBuffer());return [...new Uint8Array(hash)].map(n=>n.toString(16).padStart(2,'0')).join('');}
$('analyze').onclick=async()=>{
  const file=selectedFile,url=$('url').value.trim();
  if(!file&&!youtubeId(url))return error('Choose an MP4 video or enter a valid public HTTPS YouTube link.');
  if(file&&(!file.name.toLowerCase().endsWith('.mp4')||file.size<32||file.size>256*1024*1024))return error('Choose an MP4 video between 32 bytes and 256 MiB.');
  $('analyze').disabled=true;stopPolling();attempt=null;activeVideo=null;shown=new Set();$('questions').replaceChildren();$('resultsButton').hidden=true;$('next').hidden=true;$('waiting').textContent='Watch the preview while analysis runs. The quiz will restart the video when its questions are ready.';$('quizCount').textContent='Preparing';page('studio');$('retry').hidden=true;$('uploadProgress').hidden=false;
  try{
    if(file){
      // Start preview within the user gesture, before hashing or network waits.
      await player({kind:'LOCAL'},file);$('stage').textContent='Checking the selected file';const sha256=await fingerprint(file);
      let v=resumeVideo?await api('/videos/'+resumeVideo.id):await api('/uploads',{method:'POST',body:{name:file.name,size:file.size,sha256}});
      if(v.sha256!==sha256||v.size!==file.size)throw Error('This is not the same file as the saved upload. Select the original video, or start a new upload from Home.');
      activeVideo=v;
      while(v.received<file.size){$('stage').textContent='Saving your video';$('uploadProgress').value=Math.round(v.received/file.size*100);$('statusDetail').textContent=`${Math.round(v.received/file.size*100)}% uploaded. Completed chunks are retained if the connection fails.`;v=await api('/uploads/'+v.id+'?offset='+v.received,{method:'PUT',body:file.slice(v.received,v.received+1024*1024),raw:true});activeVideo=v;}
      v=await api('/uploads/'+v.id+'/complete',{method:'POST'});status(v);resumeVideo=null;
    }else{const v=await api('/links',{method:'POST',body:{url}});status(v);await player(v);}
    await startAnalysis();
  }catch(e){error(e.message);if(activeVideo){$('retry').hidden=!['UPLOADED','FAILED'].includes(activeVideo.status);if(activeVideo.status==='UPLOADING'){$('stage').textContent='Upload paused';$('statusDetail').textContent='Open My learning, choose Resume upload, and reselect the same file to continue.';}}}
  finally{$('analyze').disabled=false;}
};
async function startAnalysis(){
  if(!activeVideo||analysisBusy)return;
  analysisBusy=true;$('retry').disabled=true;const id=activeVideo.id;
  try{
    $('alert').hidden=true;$('stage').textContent='Checking video metadata';
    if(activeVideo.kind==='YOUTUBE'&&!yt?.getDuration)await player(activeVideo);
    const duration=activeVideo.kind==='YOUTUBE'?await youtubeDuration():activeVideo.duration;
    if(activeVideo?.id!==id)return;
    const started=await api('/videos/'+id+'/analyze',{method:'POST',body:{durationSeconds:duration}});
    if(activeVideo?.id===id){status(started);watchJob(id);}
  }catch(e){
    if(activeVideo?.id!==id)return;
    error(e.message);
    // Recover from a lost POST response without creating another job.
    try{const saved=await api('/videos/'+id);if(activeVideo?.id!==id)return;status(saved);if(['PROCESSING','READY'].includes(saved.status))watchJob(id);}catch{
      $('stage').textContent='Connection interrupted';$('statusDetail').textContent='Reconnecting to your saved analysis...';watchJob(id);
    }
  }finally{analysisBusy=false;$('retry').disabled=false;}
}
$('retry').onclick=()=>startAnalysis();
async function beginAttempt(existing){
  attempt=existing||await api('/videos/'+activeVideo.id+'/attempts',{method:'POST'});activeVideo=attempt.video;shown=new Set();selections.clear();lateness=[];$('questions').replaceChildren();$('retry').hidden=true;$('next').hidden=false;$('waiting').textContent='Keep watching. Your first question appears at its source moment.';
  // Saved answers stay in Results; mark them seen so rewinding/resuming cannot reopen them.
  for(const key of Object.keys(attempt.answers))shown.add(Number(key));
  if(!existing)seek(0);
  updateProgress();clearInterval(clockTimer);clockTimer=setInterval(tick,100);tick();
}
function tick(){if($('studio').hidden)return;const t=currentTime();$('timeLabel').textContent=formatTime(t);if(!attempt)return;for(const i of QuizClock.due(activeVideo.questions,t,shown)){renderQuestion(i);lateness.push({questionIndex:i,scheduled:activeVideo.questions[i].timestamp,displayed:t,lateSeconds:t-activeVideo.questions[i].timestamp});}const next=QuizClock.next(activeVideo.questions,attempt.answers);$('waiting').textContent=next<0?'All answers saved. Your results are ready.':shown.has(next)?'Answer the questions below. Playback can continue.':`Next question at ${formatTime(activeVideo.questions[next].timestamp)}.`;}
function renderQuestion(index){
  if(shown.has(index))return;shown.add(index);const q=activeVideo.questions[index],card=element('article',undefined,'question');card.id='question-'+index;card.tabIndex=-1;
  const label=element('small',`QUESTION ${index+1} / ${activeVideo.questions.length} · ${formatTime(q.timestamp)}`);card.append(label,element('h3',q.question));
  const options=element('div');options.setAttribute('role','group');options.setAttribute('aria-label','Answer choices');
  q.options.forEach((text,i)=>{const button=element('button',undefined,'option');button.type='button';button.setAttribute('aria-pressed','false');button.append(element('b','ABCD'[i]),element('span',text));button.onclick=()=>{if(Object.hasOwn(attempt.answers,String(index)))return;selections.set(index,i);for(const item of options.children)item.setAttribute('aria-pressed',String(item===button));card.querySelector('.submit').disabled=false;};options.append(button);});
  const submit=element('button','Submit answer','primary submit');submit.disabled=true;submit.onclick=()=>submitAnswer(index,card);
  const feedback=element('p',undefined,'feedback');feedback.hidden=true;feedback.setAttribute('role','status');card.append(options,submit,feedback);$('questions').append(card);
  const saved=attempt.review.find(r=>r.questionIndex===index);if(saved)paintFeedback(index,saved,card);
}
async function submitAnswer(index,card){const button=card.querySelector('.submit');if(button.disabled||Object.hasOwn(attempt.answers,String(index)))return;button.disabled=true;try{const result=await api('/attempts/'+attempt.id+'/answers',{method:'POST',body:{questionIndex:index,optionIndex:selections.get(index)}});attempt.answers[index]=result.selectedIndex;attempt.score=result.score;paintFeedback(index,result,card);updateProgress();}catch(e){button.disabled=false;error(e.message);}}
function paintFeedback(index,result,card){card.querySelector('.submit').hidden=true;[...card.querySelectorAll('.option')].forEach((b,i)=>{b.disabled=true;if(i===result.correctIndex)b.classList.add('correct');else if(i===result.selectedIndex)b.classList.add('wrong');b.setAttribute('aria-pressed',String(i===result.selectedIndex));});const p=card.querySelector('.feedback'),correct=result.selectedIndex===result.correctIndex;p.hidden=false;p.classList.toggle('wrong',!correct);p.textContent=(correct?'Correct. ':'Not quite. The correct answer is '+ 'ABCD'[result.correctIndex]+'. ')+result.explanation;}
function updateProgress(){const count=Object.keys(attempt.answers).length,total=activeVideo.questions.length;$('quizCount').textContent=`${count} / ${total} answered`;$('quizProgress').max=total;$('quizProgress').value=count;$('resultsButton').hidden=count!==total;$('next').hidden=false;}
$('next').onclick=()=>{
  if(!attempt||!activeVideo)return;
  // Dismiss feedback cards only after the learner explicitly chooses Next.
  for(const key of Object.keys(attempt.answers))$('question-'+key)?.remove();
  // Refresh due questions without changing either player's time or playback state.
  tick();
  const i=QuizClock.next(activeVideo.questions,attempt.answers);
  if(i<0)return;
  if(shown.has(i))$('question-'+i).focus({preventScroll:false});
};
$('resultsButton').onclick=()=>showResults();
async function showResults(id){try{attempt=await api('/attempts/'+(id||attempt.id));activeVideo=attempt.video;pausePlayer();page('results');const percent=Math.round(attempt.score/attempt.total*100);$('scoreNumber').textContent=percent+'%';$('scoreDetail').textContent=`${attempt.score} of ${attempt.total} correct`;$('resultTitle').textContent=attempt.complete?'Learning, measured.':'Your progress so far';$('resultSummary').textContent=`${activeVideo.title} · ${Object.keys(attempt.answers).length} of ${attempt.total} answered. `+(attempt.complete?(percent>=80?'Strong understanding. Revisit the evidence to reinforce what you learned.':'Review the missed ideas below, then try another attempt.'):'You can return to the video to finish this attempt.');$('review').replaceChildren();for(const r of attempt.review){const q=activeVideo.questions[r.questionIndex],card=element('article',undefined,'card review-card');card.append(element('span',r.selectedIndex===r.correctIndex?'CORRECT':'REVISIT THIS IDEA','pill'),element('h3',`${r.questionIndex+1}. ${q.question}`),element('p','Your answer: '+q.options[r.selectedIndex]),element('p','Correct answer: '+q.options[r.correctIndex]),element('p',r.explanation),element('p',`Source ${formatTime(r.evidence.start)}–${formatTime(r.evidence.end)}: ${r.evidence.evidence}`,'evidence'));const b=element('button','Revisit this moment','secondary');b.onclick=async()=>{await returnToVideo();seek(r.evidence.start);};card.append(b);$('review').append(card);}}catch(e){error(e.message);}}
async function returnToVideo(){page('studio');status(activeVideo);try{await player(activeVideo);await beginAttempt(attempt);}catch(e){error(e.message);}}
$('returnVideo').onclick=returnToVideo;
$('retryQuiz').onclick=async()=>{try{page('studio');status(activeVideo);await player(activeVideo);await beginAttempt();}catch(e){error(e.message);}};
async function openVideo(v){stopPolling();activeVideo=v;attempt=null;page('studio');$('questions').replaceChildren();$('resultsButton').hidden=true;$('next').hidden=true;$('quizCount').textContent='Loading';status(v);try{await player(v);if(v.status==='READY'){const all=await api('/attempts');const saved=all.find(a=>a.videoId===v.id&&a.answered<a.total);await beginAttempt(saved?await api('/attempts/'+saved.id):null);}else if(v.status==='PROCESSING')watchJob(v.id);}catch(e){error(e.message);}}
async function history(){if(!me)return page('auth');stopPolling();pausePlayer();page('history');$('videosList').textContent='Loading your videos…';$('attemptsList').textContent='Loading your attempts…';try{const [videos,attempts]=await Promise.all([api('/videos'),api('/attempts')]);$('videosList').replaceChildren();$('attemptsList').replaceChildren();if(!videos.length)$('videosList').textContent='Your first learning session starts with a video.';for(const v of videos){const card=element('article',undefined,'card');card.append(element('span',v.status,'pill'),element('h3',v.title),element('p',v.message));const b=element('button',v.status==='UPLOADING'?'Resume upload':v.status==='READY'?'Open quiz':'Open video','secondary');b.onclick=()=>{if(v.status==='UPLOADING'){resumeVideo=v;selectedFile=null;page('home');$('file').value='';$('fileName').textContent='Reselect '+v.title;error('Reselect the original file, then press Analyze & play. Your saved upload will resume.');}else openVideo(v);};card.append(b);$('videosList').append(card);}if(!attempts.length)$('attemptsList').textContent='Completed and in-progress attempts will appear here.';for(const a of attempts){const card=element('article',undefined,'card');card.append(element('span',a.answered===a.total?'COMPLETED':'IN PROGRESS','pill'),element('h3',a.title),element('p',`${a.score}/${a.total} correct · ${a.answered} answered · ${new Date(a.created).toLocaleString()}`));const b=element('button','Review attempt','secondary');b.onclick=()=>showResults(a.id);card.append(b);$('attemptsList').append(card);}}catch(e){error(e.message);}}
// Exposes measured render times only, without answers or credentials, for acceptance testing.
window.videoQuizTiming=()=>lateness.map(x=>({...x}));
(async()=>{try{await token();try{me=await api('/me');signedIn();}catch{page('auth');}}catch(e){page('auth');error(e.message);}})();
