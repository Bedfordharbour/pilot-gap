package au.bhe.pilotgap;
import java.nio.file.*;
public class MeshProtoCheck {
  public static void main(String[] a) throws Exception {
    for (String line : Files.readAllLines(Paths.get("cases.txt"))) {
      byte[] b = new byte[line.length()/2]; for (int i=0;i<b.length;i++) b[i]=(byte)Integer.parseInt(line.substring(2*i,2*i+2),16);
      MeshProto.Result r = MeshProto.fromRadio(b);
      System.out.println(r.kind+" "+r.node+" "+r.myNode+" "+r.json);
    }
    StringBuilder sb=new StringBuilder(); for (byte x: MeshProto.toRadioText("PG1 0123abcd truck abc123", 12345)) sb.append(String.format("%02x",x)); System.out.println("TX "+sb);
    sb=new StringBuilder(); for (byte x: MeshProto.toRadioWantConfig(777)) sb.append(String.format("%02x",x)); System.out.println("WC "+sb);
  }
}
