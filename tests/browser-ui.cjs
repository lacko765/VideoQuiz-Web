// Browser fixtures: no Gemini, YouTube or cloud requests. Exercises actual HTML/CSS/JS.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../src/main/resources/static'),out=path.resolve(__dirname,'../.tools/ui-artifacts');fs.mkdirSync(out,{recursive:true});
const server=http.createServer((req,res)=>{const file=path.resolve(root,'.'+new URL(req.url,'http://localhost').pathname.replace(/\/$/,'/index.html'));if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);return res.end();}res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html');res.end(fs.readFileSync(file));});
(async()=>{await new Promise(r=>server.listen(0,'127.0.0.1',r));const base='http://127.0.0.1:'+server.address().port;
const browser=await chromium.launch({executablePath:process.env.BROWSER_EXE,headless:true});const context=await browser.newContext({viewport:{width:1440,height:1000}});const page=await context.newPage();const errors=[],checks=[];page.on('pageerror',e=>errors.push(e.message));
let logged=false,video=null,attempts=[],analyses=0,failAnalysis=true;const questions=[2,8,12].map((timestamp,i)=>({index:i,question:['What is two plus three?','How many sides does a triangle have?','What is four times two?'][i],options:['Five','Three','Eight','Ten'],timestamp}));
function view(a){return {...a,video:{...video,questions},review:Object.entries(a.answers).map(([i,v])=>({questionIndex:+i,selectedIndex:v,correctIndex:+i,explanation:'Fixture explanation from the video.',evidence:{start:0,end:questions[i].timestamp,evidence:'Fixture video evidence.'}})),score:Object.entries(a.answers).filter(([i,v])=>+i===v).length,total:3,complete:Object.keys(a.answers).length===3};}
await page.route('**/api/**',async route=>{const req=route.request(),u=new URL(req.url()),p=u.pathname;let body=req.headers()['content-type']?.includes('application/octet-stream')?null:req.postDataJSON?.(),result={},code=200;
if(p==='/api/videos/video-1'&&req.method()==='DELETE'){video=null;attempts=[];code=204;}
 else if(p==='/api/settings')result={youtubeApiMs:60000,youtubePlayerMs:60000,youtubeMetadataMs:60000};
else if(p==='/api/csrf')result={token:'fixture',header:'X-CSRF-TOKEN'};
else if(p==='/api/me'){code=logged?200:401;result=logged?{username:'student',aiConfigured:true}:{message:'Please sign in to access your videos and results.'};}
else if(p==='/api/register')result={ok:true};
else if(p==='/api/login'){logged=true;result={ok:true};}
else if(p==='/api/logout'){logged=false;code=204;}
else if(p==='/api/uploads'){video={id:'video-1',kind:'LOCAL',title:body.name,status:'UPLOADING',duration:0,size:body.size,sha256:body.sha256,received:0,message:'Waiting for upload',questions:[]};result=video;}
else if(p==='/api/uploads/video-1'&&req.method()==='PUT'){video.received+=req.postDataBuffer().length;result=video;}
else if(p==='/api/uploads/video-1/complete'){video.status='UPLOADED';video.duration=18;result=video;}
else if(p==='/api/videos/video-1/media'){await route.fulfill({contentType:'video/mp4',path:path.resolve(__dirname,'fixtures/educational.mp4')});return;}
else if(p==='/api/links'){video={id:'video-1',kind:'YOUTUBE',youtubeId:'tVHOBVAFjUw',title:'Learning mathematics',status:'UPLOADED',duration:30,quizLanguage:'en',message:'Link format validated. Availability will be checked during analysis and playback.',questions:[]};result=video;}
else if(p.endsWith('/analyze')){analyses++;if(failAnalysis){video.status='FAILED';video.message='AI request timed out. Your video is retained; retry analysis.';}else{video.status='READY';video.questions=questions;video.message='Analysis complete. Your quiz is ready.';}result=video;}
else if(p==='/api/videos')result=video?[video]:[];
else if(p==='/api/videos/video-1')result=video;
else if(p==='/api/videos/video-1/attempts'){const a={id:'attempt-'+attempts.length,videoId:video.id,title:video.title,created:Date.now()+attempts.length,answers:{}};attempts.unshift(a);result=view(a);}
else if(p==='/api/attempts')result=attempts.map(a=>({...view(a),answered:Object.keys(a.answers).length}));
else if(p.startsWith('/api/attempts/')){const a=attempts.find(a=>a.id===p.split('/')[3]);if(p.endsWith('/answers')){a.answers[body.questionIndex]??=body.optionIndex;result={selectedIndex:a.answers[body.questionIndex],correctIndex:body.questionIndex,explanation:'Fixture explanation from the video.',score:view(a).score};}else result=view(a);}
else {code=404;result={message:'Unknown fixture route '+p};}
await route.fulfill({status:code,contentType:'application/json',body:code===204?'':JSON.stringify(result)});});
await page.route('https://www.youtube.com/iframe_api',route=>route.fulfill({contentType:'text/javascript',body:`setTimeout(()=>{window.YT={Player:class{constructor(id,o){this.time=0;this.duration=0;this.node=document.getElementById(id);this.node.textContent='YouTube test player';this.node.style.color='white';window.fixturePlayer=this;if(window.fixtureNeverReady)return;setTimeout(()=>{o.events.onReady({target:this});setTimeout(()=>{if(!window.fixtureNeverMetadata)this.duration=30},15000);},25000);}getDuration(){return this.duration}getCurrentTime(){return this.time}mute(){}playVideo(){}pauseVideo(){}seekTo(t){this.time=t}destroy(){this.node.remove()}}};window.onYouTubeIframeAPIReady();},15000);`}));
try{
await page.goto(base);await page.locator('#auth').waitFor({state:'visible'});await page.screenshot({path:path.join(out,'auth-desktop.png'),fullPage:true});
await page.locator('#username').fill('student');await page.locator('#password').fill('fixture-password');await page.locator('#register').click();await page.locator('#home').waitFor({state:'visible'});checks.push('Registration/login UI with HTTP fixtures');
await page.locator('#language').selectOption('ar');await page.waitForFunction(()=>document.documentElement.dir==='rtl');assert.equal(await page.locator('#historyNav').innerText(),'اختباراتي السابقة');await page.locator('#theme').click();await page.screenshot({path:path.join(out,'dashboard-ar-dark.png'),fullPage:true});
await page.reload();await page.locator('#home').waitFor({state:'visible'});assert.equal(await page.locator('html').getAttribute('lang'),'ar');assert.equal(await page.locator('html').getAttribute('data-theme'),'dark');checks.push('Arabic RTL and theme preferences survive refresh');
await page.locator('#language').selectOption('en');await page.locator('#theme').click();await page.clock.install();await page.locator('#url').fill('https://youtu.be/tVHOBVAFjUw');await page.locator('#analyze').click();await page.waitForFunction(()=>document.querySelector('script[data-videoquiz]'));
await page.clock.runFor(11000);assert.equal(await page.locator('#alert').isVisible(),false);assert.match(await page.locator('#statusDetail').innerText(),/longer/);assert.equal(analyses,0);
await page.clock.runFor(45000);await page.waitForFunction(()=>activeVideo?.status==='FAILED');assert.equal(analyses,1);checks.push('YouTube API 15s + player 25s + metadata 15s completes without 10s cancellation');
failAnalysis=false;await page.locator('#retry').click();await page.waitForFunction(()=>!!attempt);assert.equal(analyses,2);assert.equal(video.id,'video-1');checks.push('Retry after fixture AI failure retains video and produces quiz');
await page.evaluate(()=>{fixturePlayer.time=3;tick();});await page.locator('#question-0 .option').first().click();await page.locator('#language').selectOption('ar');assert.equal(await page.locator('#question-0 .option').first().getAttribute('aria-pressed'),'true');assert.equal(await page.evaluate(()=>fixturePlayer.time),3);await page.screenshot({path:path.join(out,'studio-ar.png'),fullPage:true});await page.locator('#question-0 .submit').click();await page.locator('#question-0 .feedback').waitFor({state:'visible'});
await page.locator('#next').click();assert.equal(await page.evaluate(()=>fixturePlayer.time),3);assert.equal(await page.locator('#question-1').count(),0);checks.push('Language switch retains selection and playback; Next never seeks');
await page.locator('#historyNav').click();await page.locator('#attemptsList button').first().click();await page.locator('#results').waitFor({state:'visible'});assert.equal(await page.locator('#review .review-card').count(),3);assert.equal(await page.locator('#review .correct').count(),2);checks.push('Incomplete review shows all questions but only submitted answer key');
await page.locator('#returnVideo').click();await page.clock.runFor(41000);await page.evaluate(()=>{fixturePlayer.time=15;tick();});
for(const i of [1,2]){await page.locator('#question-'+i+' .option').nth(i).click();await page.locator('#question-'+i+' .submit').click();await page.locator('#question-'+i+' .feedback').waitFor({state:'visible'});}
await page.locator('#resultsButton').click();await page.locator('#results').waitFor({state:'visible'});assert.equal(await page.locator('#scoreNumber').innerText(),'100%');assert.equal(await page.locator('#review .review-option').count(),12);await page.screenshot({path:path.join(out,'results-ar.png'),fullPage:true});
await page.locator('#retryQuiz').click();await page.clock.runFor(41000);await page.waitForFunction(()=>attempt&&Object.keys(attempt.answers).length===0);assert.equal(analyses,2);assert.equal(attempts.length,2);checks.push('Complete score/review and independent retry attempt without new AI analysis');
await page.locator('#historyNav').click();await page.locator('#attemptsList .card').first().waitFor();assert.equal(await page.locator('#attemptsList .card').count(),2);await page.screenshot({path:path.join(out,'history-ar-desktop.png'),fullPage:true});
for(const width of [360,768,1920]){await page.setViewportSize({width,height:900});for(const lang of ['en','ar']){await page.locator('#language').selectOption(lang);assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));}}
await page.setViewportSize({width:360,height:800});await page.screenshot({path:path.join(out,'history-ar-mobile.png'),fullPage:true});checks.push('History/dashboard, English/Arabic layouts at 360, 768 and 1920px without horizontal overflow');
await page.locator('#homeNav').click();await page.evaluate(()=>window.fixtureNeverReady=true);await page.locator('#url').fill('https://youtu.be/tVHOBVAFjUw');await page.locator('#analyze').click();await page.clock.runFor(61000);await page.locator('#alert').waitFor({state:'visible'});assert.equal(analyses,2);assert.equal(await page.locator('#retry').isVisible(),true);await page.evaluate(()=>window.fixtureNeverReady=false);await page.locator('#retry').click();await page.clock.runFor(41000);await page.waitForFunction(()=>!!attempt);assert.equal(analyses,3);checks.push('Player-ready timeout retains link and retry reconstructs the player');
await page.locator('#homeNav').click();await page.evaluate(()=>window.fixtureNeverMetadata=true);await page.locator('#url').fill('https://youtu.be/tVHOBVAFjUw');await page.locator('#analyze').click();await page.clock.runFor(86000);await page.locator('#alert').waitFor({state:'visible'});assert.equal(analyses,3);await page.evaluate(()=>{window.fixtureNeverMetadata=false;fixturePlayer.duration=30;});await page.locator('#retry').click();await page.waitForFunction(()=>!!attempt);assert.equal(analyses,4);checks.push('Zero metadata never reaches Gemini; retry succeeds after valid duration arrives');
await page.locator('#homeNav').click();await page.locator('#file').setInputFiles(path.resolve(__dirname,'fixtures/educational.mp4'));await page.locator('#analyze').click();await page.waitForFunction(()=>!!attempt&&activeVideo?.kind==='LOCAL');assert.ok(video.received>0);await page.waitForFunction(()=>document.querySelector('#video').duration>0);assert.equal(analyses,5);checks.push('Real H.264 MP4 preview, fingerprint, upload and quiz flow with HTTP/Gemini fixtures');
await page.locator('#historyNav').click();await page.locator('#videosList .delete-video').first().waitFor();
 page.once('dialog',dialog=>dialog.accept());await page.locator('#videosList .delete-video').first().click();
 
await page.waitForFunction(() =>
  document.querySelectorAll('#videosList .delete-video').length === 0 &&
  /(?:0\/30 videos|0 من 30 فيديو)/.test(
    document.querySelector('#storageUsage')?.textContent || ''
  )
);

 assert.equal(await page.locator('#videosList .delete-video').count(),0);assert.equal(await page.locator('#attemptsList .card').count(),0);
 checks.push('Deletion confirmation removes owned video and related history and refreshes quota');
 await page.locator('#logout').click();await page.locator('#auth').waitFor({state:'visible'});checks.push('Logout UI');assert.deepEqual(errors,[]);
}finally{fs.writeFileSync(path.join(out,'report.json'),JSON.stringify({checks,errors},null,2));console.log(JSON.stringify({checks,errors},null,2));await browser.close();server.close();}
})().catch(e=>{console.error(e);server.close();process.exitCode=1;});
