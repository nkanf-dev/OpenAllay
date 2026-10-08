package dev.openallay.build;
import java.nio.ByteBuffer;import java.nio.CharBuffer;import java.nio.charset.*;import java.nio.file.*;import java.util.*;
/** Public Files.writeString native facts only; no private JDK APIs or helper assumptions. */
public final class PublicCharsetWriteProbe {
 public static void main(String[]args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("freshExternalDirectory");Path dir=Paths.get(args[0]);Files.createDirectory(dir);
  Map<String,Charset> charsets=new LinkedHashMap<>();charsets.put("UTF8",StandardCharsets.UTF_8);charsets.put("UTF16",StandardCharsets.UTF_16);charsets.put("UTF16BE",StandardCharsets.UTF_16BE);charsets.put("ASCII",StandardCharsets.US_ASCII);charsets.put("CUSTOM_UTF8",new WrappedUtf8());
  Map<String,String> values=new LinkedHashMap<>();values.put("loneHigh","\ud800");values.put("loneLow","\udc00");values.put("validPair","\ud83d\udc1d");values.put("nonAscii","Ω");
  List<String> rows=new ArrayList<>();int index=0;
  for(var charset:charsets.entrySet())for(var value:values.entrySet()){
   Path target=dir.resolve("probe"+(index++));String exception=null;Integer inputLength=null;String bytes=null;
   try{Files.writeString(target,value.getValue(),charset.getValue(),StandardOpenOption.CREATE_NEW);bytes=java.util.HexFormat.of().formatHex(Files.readAllBytes(target));}
   catch(Exception failure){exception=failure.getClass().getName();if(failure instanceof MalformedInputException malformed)inputLength=malformed.getInputLength();if(failure instanceof UnmappableCharacterException unmappable)inputLength=unmappable.getInputLength();}
   rows.add("{\"charset\":\""+charset.getKey()+"\",\"input\":\""+value.getKey()+"\",\"exception\":"+(exception==null?"null":"\""+exception+"\"")+",\"exceptionInputLength\":"+inputLength+",\"destinationExists\":"+Files.exists(target)+",\"bytes\":"+(bytes==null?"null":"\""+bytes+"\"")+"}");
  }
  System.out.println("{\"scope\":\"Original native modern public Files.writeString Charset facts only\",\"javaVersion\":\""+System.getProperty("java.version")+"\",\"rows\":["+String.join(",",rows)+"]}");
 }
 static final class WrappedUtf8 extends Charset {
  WrappedUtf8(){super("OpenAllayProbeUTF8",new String[0]);}
  public boolean contains(Charset other){return StandardCharsets.UTF_8.contains(other);}
  public CharsetDecoder newDecoder(){final CharsetDecoder delegate=StandardCharsets.UTF_8.newDecoder();return new CharsetDecoder(this,1,1){protected CoderResult decodeLoop(ByteBuffer input,CharBuffer output){return delegate.decode(input,output,true);}protected void implReset(){delegate.reset();}};}
  public CharsetEncoder newEncoder(){final CharsetEncoder delegate=StandardCharsets.UTF_8.newEncoder();return new CharsetEncoder(this,1,4,new byte[]{63}){protected CoderResult encodeLoop(CharBuffer input,ByteBuffer output){return delegate.encode(input,output,true);}protected void implReset(){delegate.reset();}};}
 }
}
