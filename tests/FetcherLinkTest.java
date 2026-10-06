import java.math.BigInteger; import com.grogu.yt.core.*; import com.sun.net.httpserver.*; import java.io.*; import java.net.*; import java.nio.file.*; import java.security.MessageDigest; import java.util.*; import java.util.concurrent.atomic.*;
public class T4 {
  static int fails=0; static void check(boolean ok,String m){ if(!ok){fails++;System.out.println("FAIL "+m);} else System.out.println("ok   "+m); }
  static byte[] blob = new byte[10_000_000+123]; static { new Random(7).nextBytes(blob); }
  static String md5(byte[] b) throws Exception { return new BigInteger(1, MessageDigest.getInstance("MD5").digest(b)).toString(16); }
  static HttpServer srv(String mode, AtomicInteger reqs, List<String> methods) throws Exception {
    HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    final Set<String> failedOnce = Collections.synchronizedSet(new HashSet<>());
    s.createContext("/v", ex -> { reqs.incrementAndGet(); methods.add(ex.getRequestMethod());
      String q = ex.getRequestURI().getQuery(); long a=0,b=blob.length-1; boolean hasRange=false;
      for (String kv : q.split("&")) if (kv.startsWith("range=")) { String[] r = kv.substring(6).split("-"); a=Long.parseLong(r[0]); b=Math.min(Long.parseLong(r[1]), blob.length-1); hasRange=true; }
      if (mode.equals("postonly") && ex.getRequestMethod().equals("GET")) { ex.sendResponseHeaders(403,-1); ex.close(); return; }
      if (mode.equals("flaky") && failedOnce.add(String.valueOf(a))) { ex.sendResponseHeaders(500,-1); ex.close(); return; }
      if (mode.equals("noranges")) { ex.sendResponseHeaders(200, blob.length); ex.getResponseBody().write(blob); ex.close(); return; }
      byte[] part = Arrays.copyOfRange(blob,(int)a,(int)b+1);
      if (!mode.equals("noclen")) ex.getResponseHeaders().add("Content-Range","bytes "+a+"-"+b+"/"+blob.length);
      else ex.getResponseHeaders().add("Content-Range","bytes "+a+"-"+b+"/"+blob.length);
      ex.sendResponseHeaders(206, part.length); ex.getResponseBody().write(part); ex.close(); });
    s.createContext("/gone", ex -> { reqs.incrementAndGet(); ex.sendResponseHeaders(403,-1); ex.close(); });
    s.start(); return s; }
  static void run(String mode, boolean withClen, boolean preferPost, String expectMethodFirst) throws Exception {
    AtomicInteger reqs=new AtomicInteger(); List<String> methods=Collections.synchronizedList(new ArrayList<>()); HttpServer s=srv(mode,reqs,methods);
    String url="http://127.0.0.1:"+s.getAddress().getPort()+"/v?x=1"+(withClen?"&clen="+blob.length:"");
    File f=File.createTempFile("dlx",".bin"); long[] last={0};
    long n=StreamFetcher.download(url,"UA-test",preferPost,f,(d,t)->{ last[0]=d; },Cancel.NEVER);
    check(n==blob.length && md5(Files.readAllBytes(f.toPath())).equals(md5(blob)), mode+" clen="+withClen+" post="+preferPost+": file identical ("+n+" bytes, "+reqs.get()+" requests, progress="+last[0]+")");
    if (expectMethodFirst!=null) check(methods.get(methods.size()-1).equals(expectMethodFirst), mode+": final method "+expectMethodFirst+" "+methods.subList(0,Math.min(3,methods.size())));
    s.stop(0); f.delete(); }
  public static void main(String[] a) throws Exception {
    run("normal",true,false,"GET"); run("normal",true,true,"POST"); run("normal",false,false,"GET");
    run("postonly",true,false,"POST");  // GET refused -> falls back to POST once
    run("flaky",true,false,"GET");      // 500 on first try of every chunk -> retries
    run("noranges",true,false,"GET");   // server ignores range, sends whole file
    // 403 must fail fast, not retry forever
    AtomicInteger reqs=new AtomicInteger(); HttpServer s=srv("normal",reqs,new ArrayList<>());
    try { StreamFetcher.download("http://127.0.0.1:"+s.getAddress().getPort()+"/gone?clen=100","UA",false,File.createTempFile("xxx","yyy"),null,Cancel.NEVER); check(false,"403 should throw"); }
    catch(StreamFetcher.HttpStatus e){ check(e.code==403 && reqs.get()==2, "403 fails fast after trying both methods (requests="+reqs.get()+")"); } s.stop(0);
    // cancel
    HttpServer s2=srv("normal",new AtomicInteger(),new ArrayList<>()); final AtomicInteger calls=new AtomicInteger();
    try { StreamFetcher.download("http://127.0.0.1:"+s2.getAddress().getPort()+"/v?clen="+blob.length,"UA",false,File.createTempFile("xxx","yyy"),(d,t)->{},()->calls.incrementAndGet()>3); check(false,"cancel should throw"); }
    catch(InterruptedIOException e){ check(true,"cancel -> InterruptedIOException"); } s2.stop(0);
    // link parsing
    String[][] ok={{"https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123&index=2","dQw4w9WgXcQ"},{"https://youtu.be/dQw4w9WgXcQ?si=abc","dQw4w9WgXcQ"},{"https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=z","dQw4w9WgXcQ"},{"https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ","dQw4w9WgXcQ"},{"https://www.youtube.com/shorts/dQw4w9WgXcQ","dQw4w9WgXcQ"},{"https://youtube.com/live/dQw4w9WgXcQ?feature=share","dQw4w9WgXcQ"},{"https://www.youtube.com/embed/dQw4w9WgXcQ","dQw4w9WgXcQ"},{"  https://YOUTU.BE/dQw4w9WgXcQ  ","dQw4w9WgXcQ"}};
    for(String[] c:ok) check(LinkParser.canonical(c[0]).equals("https://www.youtube.com/watch?v="+c[1]),"canonical "+c[0].trim());
    String[] badHost={"https://evil.com/watch?v=dQw4w9WgXcQ","https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ","https://vimeo.com/1","youtube.com/watch?v=dQw4w9WgXcQ","-o /etc/passwd","","file:///etc/passwd"};
    for(String c:badHost){ try{ LinkParser.canonical(c); check(false,"should reject "+c);}catch(LinkParser.Bad e){ check(e.getMessage().equals(LinkParser.BAD_HOST),"reject host: "+c);} }
    String[] noVid={"https://www.youtube.com/playlist?list=PL123","https://www.youtube.com/","https://youtu.be/","https://www.youtube.com/watch?v=short"};
    for(String c:noVid){ try{ LinkParser.canonical(c); check(false,"should reject "+c);}catch(LinkParser.Bad e){ check(e.getMessage().equals(LinkParser.NO_VIDEO),"no video: "+c);} }
    System.exit(fails==0?0:1); } }
