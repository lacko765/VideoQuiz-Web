const test=require('node:test'),assert=require('node:assert/strict');
const clock=require('../src/main/resources/static/quiz-clock.js');
const fs=require('node:fs'),vm=require('node:vm');
const app=fs.readFileSync(require('node:path').join(__dirname,'../src/main/resources/static/app.js'),'utf8');
const nextHandler=app.slice(app.indexOf("$('next').onclick="),app.indexOf("$('resultsButton').onclick="));

for(const scenario of ['future','shown','complete','inactive'])test(`Next preserves playback for ${scenario} questions`,()=>{
  let focused=0,ticks=0,removed=0;
  const controls={next:{},'question-0':{remove(){removed++;}},'question-1':{focus(){focused++;},remove(){removed++;}}};
  const forbidden=()=>assert.fail('Next must not seek, pause, play or open results');
  const context={
    $:id=>{assert.ok(controls[id],`Unexpected control: ${id}`);return controls[id];},
    QuizClock:clock,activeVideo:{questions:[{timestamp:5},{timestamp:20}]},
    attempt:scenario==='inactive'?null:{answers:scenario==='complete'?{0:1,1:2}:{0:1}},
    shown:new Set(scenario==='shown'?[0,1]:[0]),
    tick(){ticks++;},seek:forbidden,pausePlayer:forbidden,showResults:forbidden,renderQuestion:forbidden
  };
  vm.runInNewContext(nextHandler,context);
  controls.next.onclick();
  controls.next.onclick();
  assert.equal(focused,scenario==='shown'?2:0);
  assert.equal(ticks,scenario==='inactive'?0:2);
  assert.equal(removed,scenario==='inactive'?0:scenario==='complete'?4:2);
});

test('timer advances during analysis before an attempt exists',()=>{
  const controls={studio:{hidden:false},timeLabel:{}};
  vm.runInNewContext(app.slice(app.indexOf('function tick(){'),app.indexOf('function renderQuestion('))+'tick();',{
    $:id=>controls[id],currentTime:()=>73,formatTime:n=>'01:13',attempt:null
  });
  assert.equal(controls.timeLabel.textContent,'01:13');
});

test('retry recovers a lost POST response by polling the saved job',async()=>{
  const controls={retry:{},alert:{},stage:{},statusDetail:{}};
  const calls=[];let watching;
  const context={activeVideo:{id:'saved',kind:'LOCAL',duration:42},analysisBusy:false,
    $:id=>controls[id],error:()=>{},status:v=>{context.activeVideo=v;},watchJob:id=>{watching=id;},
    api:async(path,options)=>{calls.push({path,options});if(options)throw Error('timeout');return {id:'saved',status:'PROCESSING'};}
  };
  vm.runInNewContext(app.slice(app.indexOf('async function startAnalysis(){'),app.indexOf("$('retry').onclick=")),context);
  await context.startAnalysis();
  assert.equal(calls.length,2);assert.equal(calls[0].options.body.durationSeconds,42);
  assert.equal(watching,'saved');assert.equal(controls.retry.disabled,false);
});

test('repeated retry clicks send only one analysis POST',async()=>{
  const controls={retry:{},alert:{},stage:{},statusDetail:{}};let release,posts=0;
  const context={activeVideo:{id:'saved',kind:'LOCAL',duration:42},analysisBusy:false,
    $:id=>controls[id],error:()=>{},status:()=>{},watchJob:()=>{},
    api:()=>{posts++;return new Promise(resolve=>{release=resolve;});}
  };
  vm.runInNewContext(app.slice(app.indexOf('async function startAnalysis(){'),app.indexOf("$('retry').onclick=")),context);
  const first=context.startAnalysis();await context.startAnalysis();assert.equal(posts,1);
  release({id:'saved',status:'PROCESSING'});await first;
});
test('no question before its timestamp',()=>assert.deepEqual(clock.due([{timestamp:5}],4.99,new Set()),[]));
test('question appears at its timestamp',()=>assert.deepEqual(clock.due([{timestamp:5}],5,new Set()),[0]));
test('all due questions appear even if previous question is unanswered',()=>assert.deepEqual(clock.due([{timestamp:1},{timestamp:2},{timestamp:3}],4,new Set()),[0,1,2]));
test('already displayed questions are not duplicated',()=>assert.deepEqual(clock.due([{timestamp:1},{timestamp:2}],4,new Set([0])),[1]));
test('seeking backward does not remove or repeat shown questions',()=>assert.deepEqual(clock.due([{timestamp:1},{timestamp:2}],0,new Set([0,1])),[]));
test('forward seeking queues skipped questions',()=>assert.deepEqual(clock.due([{timestamp:5},{timestamp:20}],25,new Set()),[0,1]));
test('next question uses saved answer state',()=>assert.equal(clock.next([{},{}],{0:2}),1));
test('completed quiz has no next question',()=>assert.equal(clock.next([{},{}],{0:1,1:3}),-1));
test('clock rejects invalid media time',()=>assert.deepEqual(clock.due([{timestamp:0}],NaN,new Set()),[]));
test('100ms simulated polling stays within 2 seconds at 0.5x, 1x and 2x playback',()=>{for(const speed of [.5,1,2]){const questions=[{timestamp:.37},{timestamp:1.23},{timestamp:5.78}];const shown=new Set();for(let wall=0;wall<14;wall+=.1){for(const i of clock.due(questions,wall*speed,shown)){assert.ok(wall*speed-questions[i].timestamp<.201);shown.add(i);}}assert.equal(shown.size,3);}});
