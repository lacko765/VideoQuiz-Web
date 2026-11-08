/* Pure clock logic is shared with the offline test suite. */
(function(root){
  function due(questions,time,shown){return questions.map((q,i)=>({q,i})).filter(({q,i})=>Number.isFinite(time)&&time>=q.timestamp&&!shown.has(i)).map(({i})=>i);}
  function next(questions,answers){return questions.findIndex((q,i)=>!Object.prototype.hasOwnProperty.call(answers,String(i)));}
  const api={due,next}; if(typeof module!=='undefined')module.exports=api;else root.QuizClock=api;
})(typeof globalThis==='undefined'?this:globalThis);
