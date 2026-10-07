package dev.openallay.build;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.*;
import javax.tools.*;

/** Build-only compiler-symbol-aware pattern lowering. Evaluates each selected operand once. */
public final class CanonicalPatternPort {
    private record Edit(int start,int end,String replacement) {}
    private record Candidate(CompilationUnitTree unit,TreePath patternPath,InstanceOfTree pattern,BindingPatternTree binding,IfTree owner,TreePath ownerPath,boolean negative,String reason) {}
    private record Lower(Candidate candidate,List<Edit> edits,int ownerStart,int ownerEnd,String declarations,boolean negative) {}
    private static Tree unwrap(Tree tree){while(tree instanceof ParenthesizedTree p)tree=p.getExpression();return tree;}
    private static boolean leftmost(Tree condition,Tree target){condition=unwrap(condition);if(condition==target)return true;return condition instanceof BinaryTree b&&b.getKind()==Tree.Kind.CONDITIONAL_AND&&leftmost(b.getLeftOperand(),target);}
    private static boolean abrupt(StatementTree statement){if(statement instanceof ReturnTree||statement instanceof ThrowTree)return true;if(statement instanceof BlockTree block&&!block.getStatements().isEmpty())return abrupt(block.getStatements().get(block.getStatements().size()-1));return false;}
    private static Candidate candidate(CompilationUnitTree unit,TreePath path,InstanceOfTree pattern,BindingPatternTree binding){
        TreePath owner=path;while(owner!=null&&!(owner.getLeaf() instanceof IfTree))owner=owner.getParentPath();
        if(owner==null)return new Candidate(unit,path,pattern,binding,null,null,false,"Pattern is not controlled by an if statement");
        IfTree conditional=(IfTree)owner.getLeaf();Tree condition=unwrap(conditional.getCondition());boolean negative=condition instanceof UnaryTree unary&&unary.getKind()==Tree.Kind.LOGICAL_COMPLEMENT&&unwrap(unary.getExpression())==pattern;
        String reason=null;
        if(!negative&&!leftmost(condition,pattern))reason="Pattern is not the first positively evaluated if/&& operand";
        if(negative&&(conditional.getElseStatement()!=null||!abrupt(conditional.getThenStatement())||!(owner.getParentPath().getLeaf() instanceof BlockTree)))reason="Negative pattern needs a no-else direct block guard ending in return/throw";
        if(!binding.getVariable().getModifiers().getAnnotations().isEmpty())reason="Annotated binding declaration needs an explicit attributed target policy";
        return new Candidate(unit,path,pattern,binding,conditional,owner,negative,reason);
    }
    private static int position(long value){if(value<0||value>Integer.MAX_VALUE)throw new IllegalArgumentException("Missing public AST source position");return(int)value;}
    private static String apply(String text,int base,int end,List<Edit> edits){
        StringBuilder out=new StringBuilder(text.substring(base,end));List<Edit> sorted=new ArrayList<>(edits);sorted.sort(Comparator.comparingInt(Edit::start).reversed());int previous=end;
        for(Edit edit:sorted){if(edit.start()<base||edit.end()>end||edit.end()>previous)throw new IllegalArgumentException("Overlapping pattern source edits");out.replace(edit.start()-base,edit.end()-base,edit.replacement());previous=edit.start();}return out.toString();
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("completeActualSourceRoot actualProductionClasspath selectedOwners freshExternalOutput");
        Path root=Paths.get(args[0]).toAbsolutePath().normalize(),output=Paths.get(args[3]).toAbsolutePath().normalize();if(Files.exists(output)||output.startsWith(root)||root.startsWith(output))throw new IllegalArgumentException("Fresh external output only");
        Set<String> selected=new TreeSet<>(Files.readAllLines(Paths.get(args[2]),StandardCharsets.UTF_8));List<File> files=new ArrayList<>();try(var paths=Files.walk(root)){paths.filter(p->p.toString().endsWith(".java")).sorted().forEach(p->files.add(p.toFile()));}
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IllegalStateException("Actual full JDK required");DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        Map<String,byte[]> originals=new TreeMap<>(),products=new TreeMap<>();List<String> rows=new ArrayList<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--release","17","-proc:none","-encoding","UTF-8","-classpath",args[1]),null,manager.getJavaFileObjectsFromFiles(files));List<CompilationUnitTree> units=new ArrayList<>();task.parse().forEach(units::add);Trees trees=Trees.instance(task);SourcePositions positions=trees.getSourcePositions();Map<CompilationUnitTree,List<Candidate>> candidates=new LinkedHashMap<>();
            for(CompilationUnitTree unit:units){String name=root.relativize(Paths.get(unit.getSourceFile().toUri())).toString().replace(File.separatorChar,'/');if(!selected.contains(name))continue;originals.put(name,Files.readAllBytes(root.resolve(name)));List<Candidate> found=new ArrayList<>();
                new TreePathScanner<Void,Void>(){@Override public Void visitInstanceOf(InstanceOfTree tree,Void ignored){if(tree.getPattern() instanceof BindingPatternTree binding)found.add(candidate(unit,getCurrentPath(),tree,binding));return super.visitInstanceOf(tree,ignored);}}.scan(unit,null);candidates.put(unit,found);
            }
            if(!originals.keySet().equals(selected))throw new IllegalArgumentException("Selected owner closure mismatch");task.analyze();for(Diagnostic<?> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new IllegalStateException("Complete source attribution failed: "+d);
            for(var entry:candidates.entrySet()){
                CompilationUnitTree unit=entry.getKey();String name=root.relativize(Paths.get(unit.getSourceFile().toUri())).toString().replace(File.separatorChar,'/');String text=new String(originals.get(name),StandardCharsets.UTF_8);List<Candidate> sites=entry.getValue();List<String> reasons=new ArrayList<>();Map<IfTree,Integer> perIf=new IdentityHashMap<>();for(Candidate site:sites)if(site.owner()!=null)perIf.merge(site.owner(),1,Integer::sum);
                for(Candidate site:sites){if(site.reason()!=null)reasons.add("offset="+positions.getStartPosition(unit,site.pattern())+" "+site.reason());if(site.owner()!=null&&perIf.get(site.owner())>1)reasons.add("Multiple binding patterns in one if need boolean flow expansion");}
                if(!reasons.isEmpty()){rows.add(name+"\tREJECTED\t"+sites.size()+"\t"+Base64.getEncoder().encodeToString(String.join("\n",reasons).getBytes(StandardCharsets.UTF_8)));continue;}
                List<Lower> lowered=new ArrayList<>();int index=0;
                for(Candidate site:sites){
                    String prefix;do{prefix="$oaPattern"+index++ +"_";}while(text.contains(prefix));String object=prefix+"value",match=prefix+"match",bound=prefix+"bound";
                    int exprStart=position(positions.getStartPosition(unit,site.pattern().getExpression())),exprEnd=position(positions.getEndPosition(unit,site.pattern().getExpression()));Tree type=site.binding().getVariable().getType();String typeText=text.substring(position(positions.getStartPosition(unit,type)),position(positions.getEndPosition(unit,type)));String operand=text.substring(exprStart,exprEnd);
                    String declarations="final java.lang.Object "+object+" = "+operand+";\nfinal boolean "+match+" = "+object+" instanceof "+typeText+";\n"+typeText+" "+bound+" = "+match+" ? ("+typeText+") "+object+" : null;\n";
                    Element binding=trees.getElement(TreePath.getPath(unit,site.binding().getVariable()));if(binding==null)throw new IllegalStateException("Missing original binding element");List<Edit> edits=new ArrayList<>();int start=position(positions.getStartPosition(unit,site.pattern())),end=position(positions.getEndPosition(unit,site.pattern()));edits.add(new Edit(start,end,match));
                    final boolean[] escapes={false};int ownerStart=position(positions.getStartPosition(unit,site.owner())),ownerEnd=position(positions.getEndPosition(unit,site.owner()));
                    new TreePathScanner<Void,Void>(){@Override public Void visitIdentifier(IdentifierTree tree,Void ignored){if(binding.equals(trees.getElement(getCurrentPath()))){int from=position(positions.getStartPosition(unit,tree)),to=position(positions.getEndPosition(unit,tree));if(!site.negative()&&(from<ownerStart||to>ownerEnd))escapes[0]=true;edits.add(new Edit(from,to,bound));}return super.visitIdentifier(tree,ignored);}}.scan(unit,null);
                    if(escapes[0])throw new IllegalArgumentException("Positive binding escapes its if lexical scope");lowered.add(new Lower(site,edits,ownerStart,ownerEnd,declarations,site.negative()));
                }
                // Compose nested if replacements from innermost to outermost. Symbol edits outside
                // a negative guard remain in the parent block; nested conditional evaluation stays lazy.
                List<Edit> all=new ArrayList<>();for(Lower lower:lowered)all.addAll(lower.edits());lowered.sort(Comparator.comparingInt((Lower l)->l.ownerEnd()-l.ownerStart()));
                for(Lower lower:lowered){List<Edit> inner=new ArrayList<>();for(Edit edit:all)if(edit.start()>=lower.ownerStart()&&edit.end()<=lower.ownerEnd())inner.add(edit);String statement=apply(text,lower.ownerStart(),lower.ownerEnd(),inner);all.removeAll(inner);String replacement=lower.negative()?lower.declarations()+statement:"{\n"+lower.declarations()+statement+"\n}";all.add(new Edit(lower.ownerStart(),lower.ownerEnd(),replacement));}
                String result=apply(text,0,text.length(),all);products.put(name,result.getBytes(StandardCharsets.UTF_8));rows.add(name+"\tSUPPORTED\t"+sites.size()+"\t0");
            }
        }
        for(var original:originals.entrySet())if(!Arrays.equals(original.getValue(),Files.readAllBytes(root.resolve(original.getKey()))))throw new IllegalStateException("Canonical input drift");Files.createDirectories(output);for(var product:products.entrySet())for(String side:List.of("pre","post")){Path path=output.resolve(side).resolve(product.getKey());Files.createDirectories(path.getParent());Files.write(path,side.equals("pre")?originals.get(product.getKey()):product.getValue());}Files.write(output.resolve("owner-status.tsv"),rows,StandardCharsets.UTF_8);System.out.println("PASS public attributed pattern conversion preflight owners="+originals.size()+" supported="+products.size());
    }
}
