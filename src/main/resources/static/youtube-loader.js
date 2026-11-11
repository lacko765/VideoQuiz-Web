(function(root){
  'use strict';
  function createLoader(win,doc,timers=globalThis){
    let pending;
    return function load(timeoutMs=60000,onSlow=()=>{}){
      if(win.YT?.Player)return Promise.resolve();
      if(pending)return pending;
      pending=new Promise((resolve,reject)=>{
        const script=doc.createElement('script');script.src='https://www.youtube.com/iframe_api';script.dataset.videoquiz='youtube-api';
        const previous=win.onYouTubeIframeAPIReady;
        let settled=false;
        const slow=timers.setTimeout(onSlow,10000);
        const timeout=timers.setTimeout(()=>finish(Error('YouTube connection timed out. Your video link is saved. Check your connection and retry.')),timeoutMs);
        function finish(problem){
          if(settled)return;settled=true;timers.clearTimeout(slow);timers.clearTimeout(timeout);
          if(win.onYouTubeIframeAPIReady===ready)win.onYouTubeIframeAPIReady=previous;
          if(problem){script.remove();reject(problem);}else resolve();
        }
        function ready(){if(win.YT?.Player)finish();if(typeof previous==='function')previous();}
        win.onYouTubeIframeAPIReady=ready;
        script.onerror=()=>finish(Error('YouTube could not be reached. Check your connection or content blocker, then retry.'));
        doc.head.append(script);
      }).catch(e=>{pending=null;throw e;});
      return pending;
    };
  }
  const codes={2:'The YouTube video link is invalid.',5:'YouTube cannot play this video in this browser. Try another browser or video.',100:'This YouTube video is private, removed or unavailable.',101:'The video owner does not allow embedded playback. Choose another public video.',150:'The video owner does not allow embedded playback. Choose another public video.',153:'YouTube could not verify this website. Check browser privacy settings or try another browser.'};
  const api={createLoader,errorMessage:code=>codes[code]||'YouTube playback failed. Check your connection or try another public video.'};
  if(typeof module!=='undefined')module.exports=api;else root.YouTubeLoader=api;
})(globalThis);
