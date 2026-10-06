import com.grogu.yt.audio.*; import java.io.*; import java.nio.file.*; import java.util.*;
public class T2 { public static void main(String[] a) throws Exception {
  byte[] src = Files.readAllBytes(Paths.get("/tmp/t/src.aac")); byte[] cover = Files.readAllBytes(Paths.get("/tmp/t/cover.jpg"));
  List<int[]> fr = new ArrayList<>(); int p=0; while(p+7<=src.length){ int fl=((src[p+3]&3)<<11)|((src[p+4]&0xff)<<3)|((src[p+5]&0xff)>>5); fr.add(new int[]{p,fl}); p+=fl; }
  System.out.println("frames: "+fr.size());
  byte[] asc = {0x12, 0x10}; // AAC-LC, 44.1k, stereo
  ByteArrayOutputStream bo = new ByteArrayOutputStream(); AdtsWriter w = new AdtsWriter(bo, asc, 44100, 2);
  for (int[] f: fr) w.write(src, f[0]+7, f[1]-7);
  System.out.println("ADTS identical to ffmpeg's: "+Arrays.equals(bo.toByteArray(), Arrays.copyOf(src,p)));
  M4aWriter m = new M4aWriter(new File("/tmp/t/out.m4a"), 44100, 2, asc, 128000, new Meta("Héllo – Test","Grogu & Co","GroguYt",cover));
  for (int i=0;i<fr.size();i++) m.addSample(src, fr.get(i)[0]+7, fr.get(i)[1]-7, Math.round(i*1024*1e6/44100));
  m.finish();
  M4aWriter m2 = new M4aWriter(new File("/tmp/t/out_notags.m4a"), 44100, 2, null, 0, null);
  for (int i=0;i<fr.size();i++) m2.addSample(src, fr.get(i)[0]+7, fr.get(i)[1]-7, Math.round(i*1024*1e6/44100));
  m2.finish();
} }
