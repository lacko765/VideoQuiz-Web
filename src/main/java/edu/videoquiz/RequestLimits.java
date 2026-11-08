package edu.videoquiz;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/** Small single-server request and credential-guess limits; no password or API key logging. */
@Component @Order(-110)
public class RequestLimits extends OncePerRequestFilter {
    private record Window(long start, int count) {}
    private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String path=req.getRequestURI();
        res.setHeader("Cache-Control","no-store"); res.setHeader("Referrer-Policy","strict-origin-when-cross-origin");
        long max=path.startsWith("/api/uploads/") && req.getMethod().equals("PUT") ? 1024*1024 : 65536;
        if(req.getContentLengthLong()>max) { reject(res,413,"Request is too large."); return; }
        if(req.getMethod().equals("POST") && (path.equals("/api/login") || path.equals("/api/register"))) {
            long now=System.currentTimeMillis();
            if(windows.size()>10000) windows.entrySet().removeIf(e -> now-e.getValue().start>60000);
            if(windows.size()>10000) { reject(res,429,"Server is busy. Try again in a minute."); return; }
            Window w=windows.compute(req.getRemoteAddr(),(k,v) -> v==null || now-v.start>60000 ? new Window(now,1) : new Window(v.start,v.count+1));
            if(w.count>15) { res.setHeader("Retry-After","60"); reject(res,429,"Too many sign-in attempts. Try again in one minute."); return; }
        }
        chain.doFilter(req,res);
    }
    private void reject(HttpServletResponse r,int code,String message) throws IOException { r.setStatus(code); r.setContentType("application/json"); r.getWriter().write("{\"message\":\""+message+"\"}"); }
}
