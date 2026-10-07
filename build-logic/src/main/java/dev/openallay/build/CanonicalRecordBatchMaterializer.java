package dev.openallay.build;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Review-only connected record batch; delegates every lowering to the accepted converter. */
public final class CanonicalRecordBatchMaterializer {
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String encoded(String value){return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    private static String exactNonblankFormat(String name,String body){
        boolean exact=switch(name){
            case "dev/openallay/guide/GuideService.java" -> body.equals(" Objects.requireNonNull(id, \"id\"); ");
            case "dev/openallay/guide/export/GuideSessionExportSnapshot.java" -> body.equals(" text = text == null ? \"\" : text; ");
            case "dev/openallay/guide/history/GuideHistoryMutation.java" -> body.equals(" requireSession(sessionId); ");
            case "dev/openallay/guide/ui/GuideRecipeDetailFacts.java" -> body.equals(" arguments = List.copyOf(arguments); ");
            case "dev/openallay/guide/ui/GuideToolSummaryGeometry.java" -> body.equals(" capsules = List.copyOf(capsules); ");
            case "dev/openallay/guide/ui/hud/GuideHudToolCards.java" -> body.equals(" recipes = Map.copyOf(recipes); ");
            case "dev/openallay/client/gui/GuideClientUiState.java" -> body.equals(" Objects.requireNonNull(observation, \"observation\"); ");
            case "dev/openallay/client/gui/settings/BuiltinModelSettingsProjection.java" -> body.equals(" lines = List.copyOf(lines); ") || body.equals(" arguments = List.copyOf(arguments); ");
            case "dev/openallay/client/voice/NativeModelFiles.java" -> body.equals(" files = List.copyOf(files); ");
            case "dev/openallay/client/voice/VoiceRuntime.java" -> body.equals(" Objects.requireNonNull(id); Objects.requireNonNull(kind); ");
            case "dev/openallay/client/voice/VoiceSettingsView.java" -> body.equals(" devices = List.copyOf(devices); ");
            default -> false;
        };
        return exact?body.substring(0,body.length()-1):body;
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("actualSourceRoot exactRequestTSV freshExternalOutput");
        Path root=Paths.get(args[0]).toAbsolutePath().normalize(),output=Paths.get(args[2]).toAbsolutePath().normalize();
        if(Files.exists(output)||output.startsWith(root)||root.startsWith(output))throw new IllegalArgumentException("Fresh external output required");
        List<String> statuses=new ArrayList<>();Map<String,byte[]> before=new TreeMap<>(),after=new TreeMap<>();Set<String> selectedOwners=new HashSet<>();
        for(String row:Files.readAllLines(Paths.get(args[1]),StandardCharsets.UTF_8)){
            String[] fields=row.split("\t",-1);if(fields.length!=3)throw new IllegalArgumentException("Expected source-relative path, rawSha256, exact parsed recordPaths");
            String name=fields[0];Path source=root.resolve(name).normalize();
            if(!source.startsWith(root)||!name.endsWith(".java")||!selectedOwners.add(name))throw new IllegalArgumentException("Invalid exact owner request");
            byte[] bytes=Files.readAllBytes(source);if(!sha(bytes).equals(fields[1]))throw new IllegalArgumentException("Raw preimage changed: "+name);before.put(name,bytes);
            String text=new String(bytes,StandardCharsets.UTF_8);if(!Arrays.equals(bytes,text.getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException("Invalid UTF8");
            Set<String> records=new TreeSet<>(Arrays.asList(fields[2].split(",",-1)));
            if(records.contains("")||records.size()!=fields[2].split(",",-1).length)throw new IllegalArgumentException("Duplicate/empty record request");
            Map<Integer,Set<String>> passes=new TreeMap<>(Comparator.reverseOrder());
            for(String record:records){int depth=(int)record.chars().filter(c->c=='.').count();passes.computeIfAbsent(depth,ignored->new TreeSet<>()).add(record);}
            int converted=0;String unsupported=null;
            for(Map.Entry<Integer,Set<String>> pass:passes.entrySet()){
                try{text=RecordValueSourceConverter.convert(source,text,pass.getValue());converted+=pass.getValue().size();}
                catch(IllegalArgumentException failure){unsupported="depth="+pass.getKey()+" recordPaths="+pass.getValue()+" reason="+failure.getMessage();break;}
            }
            if(unsupported!=null){statuses.add(name+"\tREJECTED\t"+records.size()+"\t0\t"+encoded(unsupported));}
            else{
                byte[] raw=text.getBytes(StandardCharsets.UTF_8);StringBuilder normalized=new StringBuilder();List<String> formatEdits=new ArrayList<>();int sourceLine=0;
                for(String line:text.split("(?<=\n)",-1)){
                    sourceLine++;
                    String ending=line.endsWith("\r\n")?"\r\n":line.endsWith("\n")?"\n":"";
                    String body=line.substring(0,line.length()-ending.length());
                    if(!body.isEmpty()&&body.chars().allMatch(c->c==' '||c=='\t'))body="";
                    String originalBody=body;body=exactNonblankFormat(name,body);
                    if(!body.equals(originalBody))formatEdits.add(sourceLine+"\t"+encoded(originalBody)+"\t"+encoded(body));
                    normalized.append(body).append(ending);
                }
                byte[] canonical=normalized.toString().getBytes(StandardCharsets.UTF_8);after.put(name,canonical);
                statuses.add(name+"\tSUPPORTED\t"+records.size()+"\t"+converted+"\t"+sha(raw)+"\t"+raw.length+"\t"+sha(canonical)+"\t"+canonical.length+"\t"+encoded(String.join("\n",formatEdits)));
            }
        }
        if(before.isEmpty())throw new IllegalArgumentException("Empty request");
        for(Map.Entry<String,byte[]> entry:before.entrySet())if(!Arrays.equals(entry.getValue(),Files.readAllBytes(root.resolve(entry.getKey()))))throw new IllegalStateException("Source changed during materialization");
        Files.createDirectories(output);
        for(Map.Entry<String,byte[]> entry:after.entrySet())for(String side:List.of("pre","post")){
            Path path=output.resolve(side).resolve(entry.getKey());Files.createDirectories(path.getParent());Files.write(path,side.equals("pre")?before.get(entry.getKey()):entry.getValue());
        }
        Files.write(output.resolve("owner-status.tsv"),statuses,StandardCharsets.UTF_8);
        System.out.println("PASS actual accepted-converter record shape classification: requestedOwners="+before.size()+" supportedOwners="+after.size()+" rejectedOwners="+(before.size()-after.size()));
    }
}
