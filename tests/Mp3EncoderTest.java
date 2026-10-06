import com.grogu.yt.audio.*; import java.io.*; import java.nio.file.*;
public class T1 { public static void main(String[] a) throws Exception {
  byte[] pcm = Files.readAllBytes(Paths.get("/tmp/t/in.pcm")); byte[] cover = Files.readAllBytes(Paths.get("/tmp/t/cover.jpg"));
  Meta m = new Meta("Héllo – Test", "Grogu & Co", "Grogut", cover);
  for (int br : new int[]{128, 320, Format.BEST_VBR}) {
    long t0=System.currentTimeMillis();
    File f = new File("/tmp/t/out_"+br+".mp3");
    Mp3Encoder e = new Mp3Encoder(f, 48000, 2, br, m);
    for (int o=0;o<pcm.length;o+=8192*4) e.write(pcm, o, Math.min(8192*4, pcm.length-o));
    e.finish();
    System.out.println(br+" -> "+f.length()+" bytes in "+(System.currentTimeMillis()-t0)+" ms");
  } } }
