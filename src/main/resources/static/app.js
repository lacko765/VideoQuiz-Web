'use strict';
const $=id=>document.getElementById(id);
const sections=['auth','home','studio','results','history'];
let csrf,me,activeVideo,attempt,selectedFile,resumeVideo,blobUrl,yt,ytPromise,pollTimer,clockTimer;
let shown=new Set(),selections=new Map(),lateness=[],pollGeneration=0;
let playerGeneration=0,analysisBusy=false,pollFailures=0,ytReady=false,ytFailure=null;
const settings={youtubeApiMs:60000,youtubePlayerMs:60000,youtubeMetadataMs:60000};
const loadYouTubeApi=YouTubeLoader.createLoader(window,document);
function slowVideo(){ $('statusDetail').textContent='YouTube is taking longer than usual. Still connecting; your video link is saved.'; }
function content(tag,text,cls){const e=element(tag,text,cls);e.dataset.verbatim='';return e;}
function page(name){$('homeNav').setAttribute('aria-current',name==='home'?'page':'false');$('historyNav').setAttribute('aria-current',name==='history'?'page':'false');for(const id of sections)$(id).hidden=id!==name;$('alert').hidden=true;window.scrollTo({top:0,behavior:'auto'});}
function error(message){$('alert').textContent=message==='Failed to fetch'?'Connection interrupted. Your saved work is retained. Check your connection and retry.':message;$('alert').hidden=false;}
function formatTime(n){n=Math.max(0,Math.floor(n||0));return `${Math.floor(n/60).toString().padStart(2,'0')}:${(n%60).toString().padStart(2,'0')}`;}
function element(tag,text,cls){const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(cls)e.className=cls;return e;}
async function token(){const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),60000);try{const r=await fetch('/api/csrf',{credentials:'same-origin',cache:'no-store',signal:controller.signal});if(!r.ok)throw Error('Cannot establish a secure session.');csrf=await r.json();}catch(e){if(e.name==='AbortError')throw Error('Cannot establish a session: the server has not responded within 60 seconds.');throw e;}finally{clearTimeout(timer);}}
async function api(path,{method='GET',body,raw=false,timeout=60000}={}){
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),timeout);
  try{
    const headers={};if(method!=='GET'&&csrf)headers[csrf.header]=csrf.token;
    if(body!==undefined)headers['Content-Type']=raw?'application/octet-stream':'application/json';
    const r=await fetch('/api'+path,{method,headers,body:body===undefined?undefined:raw?body:JSON.stringify(body),signal:controller.signal,credentials:'same-origin',cache:'no-store'});
    if(r.status===204)return null;
    const result=await r.json();
    if(!r.ok){if(r.status===401&&me){me=null;attempt=null;activeVideo=null;historyData=null;dashboardItems=[];stopPolling();resetPlayer();for(const id of ['videosList','attemptsList','review','recentActivity','dashboardStats','historyStats','performance'])$(id).replaceChildren();page('auth');$('logout').hidden=true;$('userLabel').textContent='';}throw Error(result.message||`Request failed (${r.status}).`);}
    return result;
  }catch(e){if(e.name==='AbortError')throw Error('The server has not responded within 60 seconds. Check your connection; saved upload chunks and answers are retained.');throw e;}
  finally{clearTimeout(timer);}
}
async function signIn(create){
  if(!/^[a-z0-9_]{3,32}$/.test($('username').value)||$('password').value.length<10||new TextEncoder().encode($('password').value).length>72){error('Use a 3–32 character lowercase username and a password of at least 10 characters (maximum 72 UTF-8 bytes).');return;}
  const buttons=[...$('authForm').querySelectorAll('button')];buttons.forEach(b=>b.disabled=true);
  try{
    await token();const username=$('username').value,password=$('password').value;
    if(create)await api('/register',{method:'POST',body:{username,password}});
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),60000);
    let r;try{r=await fetch('/api/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded',[csrf.header]:csrf.token},body:new URLSearchParams({username,password}),credentials:'same-origin',signal:controller.signal});}finally{clearTimeout(timer);}
    if(!r.ok){const body=await r.json();throw Error(body.message||'Could not sign in.');}
    $('password').value='';await token();me=await api('/me');signedIn();
  }catch(e){error(e.name==='AbortError'?'Sign-in timed out. Check your connection and retry.':e.message);}finally{buttons.forEach(b=>b.disabled=false);}
}
function signedIn(){$('userLabel').textContent=me.username;$('logout').hidden=false;page('home');loadDashboard();if(!me.aiConfigured)error('The server needs GEMINI_API_KEY before analysis can run. Your uploaded files can still be saved.');}
$('authForm').noValidate=true;
$('authForm').addEventListener('submit',e=>{e.preventDefault();signIn(false);});$('register').onclick=()=>signIn(true);
$('logout').onclick=async()=>{try{await api('/logout',{method:'POST'});stopPolling();resetPlayer();attempt=null;activeVideo=null;selectedFile=null;resumeVideo=null;me=null;historyData=null;dashboardItems=[];for(const id of ['videosList','attemptsList','review','recentActivity','dashboardStats','historyStats','performance'])$(id).replaceChildren();$('file').value='';$('url').value='';$('userLabel').textContent='';$('logout').hidden=true;page('auth');await token();}catch(e){error(e.message);}};
function home(){if(!me)return page('auth');stopPolling();pausePlayer();resumeVideo=null;page('home');loadDashboard();}
for(const id of ['homeNav','backHome','historyNew'])$(id).onclick=home;
$('historyNav').onclick=history;
document.querySelector('.brand').onclick=e=>{e.preventDefault();home();};
$('file').onchange=()=>{selectedFile=$('file').files[0];if(selectedFile){$('fileName').textContent=selectedFile.name;$('url').value='';}};
$('url').oninput=()=>{selectedFile=null;resumeVideo=null;$('file').value='';$('fileName').textContent='Choose a video file';};
function youtubeId(value){try{const u=new URL(value);if(u.protocol!=='https:'||u.username||u.password||u.port||u.hash)return null;let id;if(u.hostname==='youtu.be')id=u.pathname.slice(1);else if(['youtube.com','www.youtube.com','m.youtube.com'].includes(u.hostname)){if(u.pathname==='/watch'&&u.searchParams.getAll('v').length===1)id=u.searchParams.get('v');else if(/^\/(shorts|embed)\/[A-Za-z0-9_-]{11}$/.test(u.pathname))id=u.pathname.split('/').pop();}return /^[A-Za-z0-9_-]{11}$/.test(id||'')?id:null;}catch{return null;}}
function resetPlayer(){playerGeneration++;ytReady=false;ytFailure=null;clearInterval(clockTimer);if(yt){yt.destroy();yt=null;}if(!$('youtube')){const host=element('div');host.id='youtube';document.querySelector('.player-shell').append(host);}if(blobUrl){URL.revokeObjectURL(blobUrl);blobUrl=null;}$('video').pause();$('video').removeAttribute('src');$('video').load();$('video').hidden=true;$('youtube').hidden=true;$('playerEmpty').hidden=false;}
function pausePlayer(){try{if(yt)yt.pauseVideo();else $('video').pause();}catch{}}
function currentTime(){try{return yt?yt.getCurrentTime():$('video').currentTime;}catch{return 0;}}
function seek(t){try{if(yt){yt.seekTo(t,true);yt.playVideo();}else{$('video').currentTime=t;$('video').play().catch(()=>{$('playbackMessage').textContent='Press Play in the video controls to continue.';});}}catch{}}
function loadYouTube(){ $('stage').textContent='Connecting to YouTube';return loadYouTubeApi(settings.youtubeApiMs,slowVideo); }
async function youtubeDuration(){
  const generation=playerGeneration,deadline=Date.now()+settings.youtubeMetadataMs;
  $('stage').textContent='Loading video information';
  const started=Date.now();
  while(Date.now()<deadline){
    if(ytFailure)throw Error(ytFailure);
    if(Date.now()-started>10000)slowVideo();
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
    $('stage').textContent='Loading the YouTube player';
    try { await new Promise((resolve,reject)=>{
      const slow=setTimeout(slowVideo,10000);
      const timer=setTimeout(()=>{clearTimeout(slow);reject(Error('YouTube player initialization timed out. Your link is saved. Retry loading the video.'));},settings.youtubePlayerMs);
      const done=()=>{clearTimeout(timer);clearTimeout(slow);};
      yt=new YT.Player('youtube',{videoId:video.youtubeId,host:'https://www.youtube-nocookie.com',playerVars:{playsinline:1,origin:location.origin,rel:0},events:{onReady:e=>{done();if(generation!==playerGeneration){reject(Error('Video changed.'));return;}ytReady=true;ytFailure=null;e.target.mute();e.target.playVideo();$('playbackMessage').textContent='Playback starts muted. Use the player controls to enable sound.';resolve();},onError:e=>{done();if(generation!==playerGeneration)return;ytReady=false;ytFailure=YouTubeLoader.errorMessage(e.data);error(ytFailure);$('retry').hidden=false;reject(Error(ytFailure));}}});
    }); } catch(e) {if(generation===playerGeneration){if(yt)yt.destroy();yt=null;ytReady=false;}throw e;}
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
    $('alert').hidden=true;
    if(['PROCESSING','READY'].includes(activeVideo.status)){if(activeVideo.kind==='YOUTUBE'&&!ytReady)await player(activeVideo);if(activeVideo.status==='READY'&&attempt){tick();return;}watchJob(id);return;}
    $('stage').textContent='Checking video metadata';
    if(activeVideo.kind==='YOUTUBE'&&(!ytReady||ytFailure||!yt?.getDuration))await player(activeVideo);
    const duration=activeVideo.kind==='YOUTUBE'?await youtubeDuration():activeVideo.duration;
    if(activeVideo?.id!==id)return;
    $('stage').textContent='Sending video for AI analysis';
    const started=await api('/videos/'+id+'/analyze',{method:'POST',body:{durationSeconds:duration,language:$('quizLanguage').value}});
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
  const label=element('small',`QUESTION ${index+1} / ${activeVideo.questions.length} · ${formatTime(q.timestamp)}`);card.append(label,content('h3',q.question));
  const options=element('div');options.setAttribute('role','group');options.setAttribute('aria-label','Answer choices');
  q.options.forEach((text,i)=>{const button=element('button',undefined,'option');button.type='button';button.setAttribute('aria-pressed','false');button.append(element('b','ABCD'[i]),content('span',text));button.onclick=()=>{if(Object.hasOwn(attempt.answers,String(index)))return;selections.set(index,i);for(const item of options.children)item.setAttribute('aria-pressed',String(item===button));card.querySelector('.submit').disabled=false;};options.append(button);});
  const submit=element('button','Submit answer','primary submit');submit.disabled=true;submit.onclick=()=>submitAnswer(index,card);
  const feedback=element('p',undefined,'feedback');feedback.hidden=true;feedback.setAttribute('role','status');card.append(options,submit,feedback);$('questions').append(card);
  const saved=attempt.review.find(r=>r.questionIndex===index);if(saved)paintFeedback(index,saved,card);
}
async function submitAnswer(index,card){const button=card.querySelector('.submit');if(button.disabled||Object.hasOwn(attempt.answers,String(index)))return;button.disabled=true;try{const result=await api('/attempts/'+attempt.id+'/answers',{method:'POST',body:{questionIndex:index,optionIndex:selections.get(index)}});attempt.answers[index]=result.selectedIndex;attempt.score=result.score;attempt.review=attempt.review.filter(r=>r.questionIndex!==index);attempt.review.push({...result,questionIndex:index});paintFeedback(index,result,card);updateProgress();}catch(e){button.disabled=false;error(e.message);}}
function paintFeedback(index,result,card){
  card.querySelector('.submit').hidden=true;
  [...card.querySelectorAll('.option')].forEach((b,i)=>{b.disabled=true;if(i===result.correctIndex)b.classList.add('correct');else if(i===result.selectedIndex)b.classList.add('wrong');b.setAttribute('aria-pressed',String(i===result.selectedIndex));});
  const p=card.querySelector('.feedback'),correct=result.selectedIndex===result.correctIndex;p.hidden=false;p.classList.toggle('wrong',!correct);
  p.replaceChildren(element('strong',correct?'Correct.':'Not quite. Review the correct answer.'),document.createTextNode(' '),content('span',result.explanation));
}
function updateProgress(){const count=Object.keys(attempt.answers).length,total=activeVideo.questions.length;$('quizCount').textContent=`${count} / ${total} answered`;$('quizProgress').max=total;$('quizProgress').value=count;$('resultsButton').hidden=count!==total;$('next').hidden=false;}
$('next').onclick=()=>{
  if(!attempt||!activeVideo)return;
  // Dismiss feedback cards only after the learner explicitly chooses Next.
  for(const key of Object.keys(attempt.answers))$('question-'+key)?.remove();
  // Refresh due questions without changing either player's time or playback state.
  tick();
  const i=QuizClock.next(activeVideo.questions,attempt.answers);
  if(i<0)return;
  if(shown.has(i))$('question-'+i)?.focus({preventScroll:false});
};
$('resultsButton').onclick=()=>showResults();
async function showResults(id){try{attempt=await api('/attempts/'+(id||attempt.id));activeVideo=attempt.video;pausePlayer();page('results');renderResults();}catch(e){error(e.message);}}
function renderResults(){
  const percent=attempt.total?Math.round(attempt.score/attempt.total*100):0;
  $('scoreNumber').textContent=percent+'%';$('scoreDetail').textContent=`${attempt.score} of ${attempt.total} correct`;
  $('resultTitle').textContent=attempt.complete?'Your learning snapshot':'Your progress so far';$('resultVideoTitle').textContent=activeVideo.title;
  $('resultSummary').textContent=`${Object.keys(attempt.answers).length} of ${attempt.total} answered. `+(attempt.complete?'Review your answers and revisit the source moments.':'Return to the video to finish this attempt.');
  $('review').replaceChildren();
  activeVideo.questions.forEach((q,index)=>{
    const r=attempt.review.find(r=>r.questionIndex===index),correct=r&&r.selectedIndex===r.correctIndex;
    const card=element('article',undefined,'card review-card');
    card.append(element('span',!r?'Not answered':correct?'Correct':'Incorrect','pill '+(!r?'':correct?'correct':'wrong')),content('h3',`${index+1}. ${q.question}`));
    const options=element('div',undefined,'review-options');
    q.options.forEach((text,i)=>{const option=element('div',undefined,'review-option '+(r&&i===r.correctIndex?'correct':r&&i===r.selectedIndex?'wrong':''));
      if(r&&i===r.selectedIndex)option.append(element('span','Your answer','answer-label'));
      if(r&&i===r.correctIndex)option.append(element('span','Correct answer','answer-label'));
      option.append(content('span','ABCD'[i]+'. '+text));options.append(option);});card.append(options);
    if(r){card.append(content('p',r.explanation));if(r.evidence){const evidence=element('div',undefined,'evidence');evidence.append(element('strong',`Source ${formatTime(r.evidence.start)}–${formatTime(r.evidence.end)}`),content('p',r.evidence.evidence));card.append(evidence);const b=element('button','Revisit this moment','secondary');b.onclick=async()=>{await returnToVideo();seek(r.evidence.start);};card.append(b);}}
    else card.append(element('p','Submit an answer in the video session to unlock feedback.','hint'));
    $('review').append(card);
  });
}
async function returnToVideo(){page('studio');status(activeVideo);try{await player(activeVideo);await beginAttempt(attempt);}catch(e){error(e.message);}}
$('returnVideo').onclick=returnToVideo;
$('retryQuiz').onclick=async()=>{try{page('studio');status(activeVideo);await player(activeVideo);await beginAttempt();}catch(e){error(e.message);}};
async function openVideo(v){stopPolling();activeVideo=v;attempt=null;page('studio');$('questions').replaceChildren();$('resultsButton').hidden=true;$('next').hidden=true;$('quizCount').textContent='Loading';status(v);try{await player(v);if(v.status==='READY'){const all=await api('/attempts');const saved=all.find(a=>a.videoId===v.id&&a.answered<a.total);await beginAttempt(saved?await api('/attempts/'+saved.id):null);}else if(v.status==='PROCESSING')watchJob(v.id);}catch(e){error(e.message);}}
let historyData=null,historyLimit=12,dashboardItems=[];
function stats(items,target){
  const complete=items.filter(a=>a.answered===a.total&&a.total>0),answered=items.reduce((n,a)=>n+a.answered,0),correct=items.reduce((n,a)=>n+a.score,0);
  const avg=complete.length?Math.round(complete.reduce((n,a)=>n+100*a.score/a.total,0)/complete.length):0;
  $(target).replaceChildren();for(const [label,value] of [['Completed quizzes',complete.length],['Average completed score',avg+'%'],['Questions answered',answered],['Correct answers',correct],['Incorrect answers',answered-correct]]){const box=element('div',undefined,'stat');box.append(element('strong',String(value)),element('span',label));$(target).append(box);}
}
function renderDashboard(items){dashboardItems=items;stats(items,'dashboardStats');$('recentActivity').replaceChildren();if(!items.length)$('recentActivity').append(element('p','Your learning journey starts with your first video.','empty'));
  for(const a of items.slice(0,3)){const row=element('div',undefined,'recent-row'),info=element('div');info.append(content('strong',a.title),element('small',`${a.score}/${a.total} correct · ${I18n.date(a.created)}`));const button=element('button','Review','secondary');button.onclick=()=>showResults(a.id);row.append(info,button);$('recentActivity').append(row);}}
async function loadDashboard(){const user=me?.username;if(!user)return;try{const items=await api('/attempts');if(me?.username===user)renderDashboard(items);}catch(e){$('recentActivity').textContent='Progress could not be loaded. Open My Quizzes to retry.';}}
async function history(){if(!me)return page('auth');stopPolling();pausePlayer();page('history');historyData=null;historyLimit=12;$('videosList').textContent='Loading your videos…';$('attemptsList').textContent='Loading your attempts…';const user=me.username;try{const [videos,attempts]=await Promise.all([api('/videos'),api('/attempts')]);if(me?.username!==user)return;historyData={videos,attempts:attempts.sort((a,b)=>b.created-a.created)};renderHistory();}catch(e){error(e.message);}}
function renderHistory(){
  if(!historyData)return;const {videos,attempts}=historyData;stats(attempts,'historyStats');renderDashboard(attempts);$('videosList').replaceChildren();$('attemptsList').replaceChildren();$('performance').replaceChildren();
  if(!videos.length)$('videosList').append(element('p','Your first learning session starts with a video.','empty'));
  for(const v of videos){const card=element('article',undefined,'card');card.append(element('span',v.status,'pill'),content('h3',v.title),element('p',v.message));const b=element('button',v.status==='UPLOADING'?'Resume upload':v.status==='READY'?'Open quiz':'Open video','secondary');b.onclick=()=>{if(v.status==='UPLOADING'){resumeVideo=v;selectedFile=null;page('home');$('file').value='';$('fileName').textContent=v.title;error('Reselect the original file, then press Analyze & play. Your saved upload will resume.');}else openVideo(v);};card.append(b);$('videosList').append(card);}
  if(!attempts.length){$('attemptsList').append(element('p','Completed and in-progress attempts will appear here.','empty'));$('performance').append(element('p','Complete a quiz to start tracking your progress.','empty'));}
  for(const a of attempts.slice(0,historyLimit)){const card=element('article',undefined,'card');card.append(element('span',a.answered===a.total?'Completed':'In progress','pill'),content('h3',a.title),content('p',I18n.date(a.created)),element('div',Math.round(a.total?100*a.score/a.total:0)+'%','attempt-score'));const details=element('div',undefined,'attempt-details');details.append(element('span',`${a.total} questions`),element('span',`${a.score} correct`),element('span',`${a.answered-a.score} incorrect`),element('span',`${a.total-a.answered} unanswered`));card.append(details);const b=element('button','View full results','secondary');b.onclick=()=>showResults(a.id);card.append(b);$('attemptsList').append(card);}
  if(attempts.length&&!attempts.some(a=>a.answered===a.total))$('performance').append(element('p','Complete a quiz to start tracking your progress.','empty'));
  if(attempts.length>historyLimit){const more=element('button','Show more attempts','secondary');more.onclick=()=>{historyLimit+=12;renderHistory();};$('attemptsList').append(more);}
  for(const a of attempts.filter(a=>a.answered===a.total).slice(0,8).reverse()){const row=element('div',undefined,'performance-row'),bar=element('progress');bar.max=a.total;bar.value=a.score;bar.setAttribute('aria-label',a.title);row.append(content('span',a.title),bar,element('span',Math.round(a.score/a.total*100)+'%'));$('performance').append(row);}
}
const drop=$('dropZone');drop.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();$('file').click();}};
for(const event of ['dragenter','dragover'])drop.addEventListener(event,e=>{e.preventDefault();drop.classList.add('dragover');});
for(const event of ['dragleave','drop'])drop.addEventListener(event,e=>{e.preventDefault();drop.classList.remove('dragover');});
drop.addEventListener('drop',e=>{const files=e.dataTransfer.files;if(files.length!==1)return error('Drop one MP4 video at a time.');selectedFile=files[0];resumeVideo=null;$('fileName').textContent=selectedFile.name;$('url').value='';});
window.addEventListener('videoquiz-language',()=>{if(historyData)renderHistory();else if(me)renderDashboard(dashboardItems);if(attempt&&!$('results').hidden)renderResults();});
// Exposes measured render times only, without answers or credentials, for acceptance testing.
window.videoQuizTiming=()=>lateness.map(x=>({...x}));
(async()=>{try{Object.assign(settings,await api('/settings'));await token();try{me=await api('/me');signedIn();}catch{page('auth');}}catch(e){page('auth');error(e.message);}})();
