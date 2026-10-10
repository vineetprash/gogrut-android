import com.grogu.yt.audio.*; import java.io.*; import java.nio.file.*; import java.util.*;
public class T3 {
  // test-only Ogg demuxer: returns packets after the 2 header packets
  static List<byte[]> packets(byte[] d, byte[][] heads) { List<byte[]> pk=new ArrayList<>(); ByteArrayOutputStream cur=new ByteArrayOutputStream(); int p=0;
    while(p+27<=d.length){ int ns=d[p+26]&0xff; int bp=p+27+ns; for(int i=0;i<ns;i++){ int l=d[p+27+i]&0xff; cur.write(d,bp,l); bp+=l; if(l<255){ pk.add(cur.toByteArray()); cur.reset(); } } p=bp; }
    heads[0]=pk.remove(0); heads[1]=pk.remove(0); return pk; }
  static void run(String name, String cover) throws Exception {
    byte[] src=Files.readAllBytes(Paths.get("/tmp/t/src.opus")); byte[][] h=new byte[2][]; List<byte[]> pk=packets(src,h);
    byte[] cv=cover==null?null:Files.readAllBytes(Paths.get(cover));
    OutputStream os=new BufferedOutputStream(new FileOutputStream("/tmp/t/"+name)); OggOpusWriter w=new OggOpusWriter(os,h[0],2,48000,new Meta("Héllo – Test","Grogu & Co","Gogrut",cv));
    long samples=0; for(byte[] p:pk){ w.writePacket(p,0,p.length); samples+=OggOpusWriter.packetSamples(p,0,p.length);} w.finish(); os.close();
    System.out.println(name+": packets="+pk.size()+" samples="+samples+" ("+samples/48000.0+" s)"); }
  public static void main(String[] a) throws Exception { run("out_a.opus","/tmp/t/cover.jpg"); run("out_b.opus","/tmp/t/bigcover.jpg"); run("out_c.opus",null); } }
