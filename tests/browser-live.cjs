// Opt-in live tests: uses real Gemini quota and creates disposable accounts in the test server.
// Run only against an isolated copy. Set VIDEOQUIZ_TEST_URL and PLAYWRIGHT_MODULE / BROWSER_EXE.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),crypto=require('node:crypto');
(async()=>{
  const url=process.env.VIDEOQUIZ_TEST_URL;
  assert.ok(/^https:\/\/localhost:\d+$/.test(url||''),'Set an isolated localhost test server URL');
  const browser=await chromium.launch({executablePath:process.env.BROWSER_EXE,headless:true});
  // This temporary browser context accepts only the test setup's self-signed HTTPS deployment.
  // It does not install certificates, modify the user's browser, or prove browser trust.
  const context=await browser.newContext({ignoreHTTPSErrors:true});
  const page=await context.newPage();const report={checks:[],videos:[],pageErrors:[]};
  page.on('pageerror',e=>report.pageErrors.push(e.message));
  try {
    await page.goto(url);await page.locator('#auth').waitFor({state:'visible'});
    const username='test_'+crypto.randomBytes(5).toString('hex'),password=crypto.randomBytes(18).toString('hex');
    await page.locator('#username').fill(username);
    await page.locator('#password').fill(password);
    await page.locator('#register').click();await page.locator('#home').waitFor({state:'visible'});
    report.checks.push('HTTPS page and account registration/login passed');
    const cases=process.env.VIDEOQUIZ_TEST_ONLY_LOCAL?[]:[{id:'tVHOBVAFjUw'},{id:'VScM8Z8Jls0'}];
    if(process.env.VIDEOQUIZ_TEST_MP4)cases.push({local:true});
    for(const entry of cases) {
      const id=entry.id;
      await page.locator('#homeNav').click();
      if(entry.local)await page.locator('#file').setInputFiles(process.env.VIDEOQUIZ_TEST_MP4);
      else await page.locator('#url').fill('https://www.youtube.com/watch?v='+id);
      await page.locator('#analyze').click();
      const item=entry.local?{kind:'LOCAL'}:{youtubeId:id};report.videos.push(item);
      try {
        if(entry.local)await page.waitForFunction(()=>document.querySelector('#video').duration>0);
        else await page.waitForFunction(id=>typeof yt!=='undefined'&&yt?.getDuration?.()>0&&yt?.getVideoData?.().video_id===id,id,{timeout:40000});
        item.duration=await page.evaluate(()=>yt?yt.getDuration():$('video').duration);
        await page.waitForFunction(()=>currentTime()>2,null,{timeout:20000});
        item.playerTime=await page.evaluate(()=>currentTime());
        item.timer=await page.locator('#timeLabel').innerText();
        assert.ok(item.playerTime>0);assert.notEqual(item.timer,'00:00');
        await page.waitForFunction(()=>activeVideo&&['READY','FAILED'].includes(activeVideo.status),null,{timeout:300000});
        item.status=await page.evaluate(()=>activeVideo.status);item.message=await page.locator('#stage').innerText();
        if(item.status==='FAILED') {
          const oldId=await page.evaluate(()=>activeVideo.id);
          await page.locator('#retry').click();
          await page.waitForFunction(()=>activeVideo.status==='PROCESSING',null,{timeout:15000});
          await page.waitForFunction(()=>['READY','FAILED'].includes(activeVideo.status),null,{timeout:300000});
          assert.equal(await page.evaluate(()=>activeVideo.id),oldId);
          item.retryStatus=await page.evaluate(()=>activeVideo.status);item.retryMessage=await page.locator('#stage').innerText();
        }
        if(await page.evaluate(()=>activeVideo.status==='READY')) {
          await page.waitForFunction(()=>!!attempt);
          const q=await page.evaluate(()=>activeVideo.questions);
          item.questionCount=q.length;assert.ok(q.length>0);assert.ok(q.every(x=>x.timestamp>0&&x.timestamp<=item.duration));
          // Explicit manual seek is a test action; Next itself must never seek.
          if(!entry.local)await page.evaluate(t=>seek(t),q[0].timestamp+0.5);
          await page.locator('#question-0').waitFor();
          await page.locator('#question-0 .option').first().click();
          await page.locator('#question-0 .submit').click();
          await page.locator('#question-0 .feedback').waitFor({state:'visible'});
          const before=await page.evaluate(()=>currentTime());
          await page.locator('#next').click();
          const after=await page.evaluate(()=>currentTime());
          assert.ok(after>=before-0.2&&after-before<3);await page.locator('#question-0').waitFor({state:'detached'});
          await page.evaluate(t=>seek(t),q[q.length-1].timestamp+0.5);
          for(let i=1;i<q.length;i++) {
            await page.locator('#question-'+i).waitFor();await page.locator('#question-'+i+' .option').first().click();
            await page.locator('#question-'+i+' .submit').click();await page.locator('#question-'+i+' .feedback').waitFor({state:'visible'});
          }
          await page.locator('#resultsButton').click();await page.locator('#results').waitFor({state:'visible'});
          assert.equal(await page.locator('#review .review-card').count(),q.length);
          item.score=await page.locator('#scoreDetail').innerText();item.quizFlow='passed';
          const answers=await page.evaluate(()=>attempt.review.map(r=>({index:r.questionIndex,correct:r.correctIndex})));
          await page.locator('#retryQuiz').click();await page.waitForFunction(()=>attempt&&Object.keys(attempt.answers).length===0);
          await page.evaluate(t=>seek(t),q[q.length-1].timestamp+0.5);
          for(const answer of answers){
            const card=page.locator('#question-'+answer.index);await card.waitFor();
            await card.locator('.option').nth(answer.correct).click();await card.locator('.submit').click();await card.locator('.feedback').waitFor({state:'visible'});
          }
          await page.locator('#resultsButton').click();await page.locator('#results').waitFor({state:'visible'});
          assert.equal(await page.locator('#scoreNumber').innerText(),'100%');item.correctAnswerScore='100%';
          await page.locator('#historyNav').click();await page.locator('#attemptsList .card').first().waitFor();
          assert.ok(await page.locator('#attemptsList .card').count()>=2);
        }
      } catch(e) { item.error=e.message.split('\n')[0];item.alert=await page.locator('#alert').textContent(); }
    }
    await page.locator('#logout').click();await page.locator('#auth').waitFor({state:'visible'});
    await page.locator('#username').fill(username);await page.locator('#password').fill(password);
    await page.locator('#authForm button[type=submit]').click();await page.locator('#home').waitFor({state:'visible'});
    report.checks.push('Explicit logout and password login passed');
  } finally {
    console.log(JSON.stringify(report,null,2));
    if(process.env.VIDEOQUIZ_TEST_REPORT)fs.writeFileSync(process.env.VIDEOQUIZ_TEST_REPORT,JSON.stringify(report,null,2));
    await browser.close();
  }
})().catch(e=>{console.error(e.message);process.exitCode=1;});
