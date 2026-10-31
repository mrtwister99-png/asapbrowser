
pub const YOUTUBE_CSS: &str = r##"
#player-ads, #masthead-ad, .ytd-ad-slot-renderer, .ytd-display-ad-renderer,
.ytd-video-masthead-ad-v3-renderer, .ytd-action-companion-ad-renderer,
.ytd-in-feed-ad-layout-renderer, .ytp-ad-overlay-container, .ytp-ad-image-overlay,
.ytp-ad-overlay-slot, .ad-showing .ytp-ad-module { display: none !important; visibility: hidden !important; height: 0 !important; }
.ytp-ad-text, .ytp-ad-duration-remaining { display: none !important; }
"##;

pub const YOUTUBE_JS: &str = r##"
(function(){
  if(window.__tomoweb_yt) return; window.__tomoweb_yt=true;
  const SKIP=[".ytp-ad-skip-button",".ytp-ad-skip-button-modern",".videoAdUiSkipButton",".ytp-skip-ad-button"];
  const HIDE=["#player-ads",".ytp-ad-overlay-container",".ytp-ad-image-overlay","ytd-ad-slot-renderer","ytd-display-ad-renderer"];
  function trySkip(){
    const v=document.querySelector("video.html5-main-video, video");
    const ad=document.querySelector(".ad-showing, .ad-interrupting");
    if(ad && v){
      try{ v.muted=true; v.playbackRate=16; if(v.duration && v.currentTime < v.duration-0.5) v.currentTime=v.duration; v.play().catch(()=>{});}catch(e){}
      SKIP.forEach(s=>{ document.querySelectorAll(s).forEach(b=>{ try{b.click();}catch(e){} }); });
    }
    HIDE.forEach(s=>{ document.querySelectorAll(s).forEach(el=>{ try{el.style.display="none"; el.remove();}catch(e){} }); });
  }
  const obs=new MutationObserver(()=>trySkip());
  function start(){ const t=document.body||document.documentElement; if(t) obs.observe(t,{childList:true,subtree:true,attributes:true,attributeFilter:["class"]}); }
  setInterval(trySkip,500); setInterval(trySkip,200);
  if(document.readyState==="loading") document.addEventListener("DOMContentLoaded", start); else start();
})();
"##;

pub fn get_css() -> &'static str { YOUTUBE_CSS }
pub fn get_js() -> String { YOUTUBE_JS.to_string() }
pub fn get_full_script() -> String {
    format!(r##"(function(){{ var s=document.createElement('style'); s.textContent=`{}`; (document.head||document.documentElement).appendChild(s); }})(); {}"##, YOUTUBE_CSS, YOUTUBE_JS)
}
