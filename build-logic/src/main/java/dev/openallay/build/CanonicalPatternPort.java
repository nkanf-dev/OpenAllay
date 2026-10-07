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
    private record Candidate(CompilationUnitTree unit,TreePath patternPath,InstanceOfTree pattern,BindingPatternTree binding,Tree owner,TreePath ownerPath,boolean negative,String reason) {}
    private record Lower(Candidate candidate,List<Edit> edits,int ownerStart,int ownerEnd,String declarations,boolean negative) {}
    private static Tree unwrap(Tree tree){while(tree instanceof ParenthesizedTree p)tree=p.getExpression();return tree;}
    private static boolean leftmost(Tree condition,Tree target){condition=unwrap(condition);if(condition==target)return true;return condition instanceof BinaryTree b&&b.getKind()==Tree.Kind.CONDITIONAL_AND&&leftmost(b.getLeftOperand(),target);}
    private static boolean abrupt(StatementTree statement){if(statement instanceof ReturnTree||statement instanceof ThrowTree)return true;if(statement instanceof BlockTree block&&!block.getStatements().isEmpty())return abrupt(block.getStatements().get(block.getStatements().size()-1));return false;}
    private static Candidate candidate(CompilationUnitTree unit,TreePath path,InstanceOfTree pattern,BindingPatternTree binding){
        TreePath anchor=path.getParentPath();String reason=null;
        while(anchor!=null){
            Tree leaf=anchor.getLeaf();
            if(leaf instanceof WhileLoopTree||leaf instanceof DoWhileLoopTree||leaf instanceof ForLoopTree||leaf instanceof EnhancedForLoopTree){reason="Repeated loop condition/initializer pattern needs per-iteration capture proof";break;}
            if(leaf instanceof LambdaExpressionTree lambda){
                if(lambda.getBodyKind()!=LambdaExpressionTree.BodyKind.EXPRESSION)reason="Block lambda pattern lacks a nearer statement anchor";
                break;
            }
            if(leaf instanceof StatementTree && !(leaf instanceof BlockTree)){break;}
            anchor=anchor.getParentPath();
        }
        if(anchor==null)reason="Pattern has no supported statement/lambda evaluation boundary";
        Tree owner=anchor==null?null:anchor.getLeaf();
        if(owner instanceof VariableTree && !(anchor.getParentPath().getLeaf() instanceof BlockTree))reason="Field/resource/for-initializer pattern lacks a local block boundary";
        if(owner!=null && !(owner instanceof LambdaExpressionTree) && !(anchor.getParentPath().getLeaf() instanceof BlockTree)
                && !(anchor.getParentPath().getLeaf() instanceof IfTree))reason="Statement boundary is not a direct block or if branch";
        if(!binding.getVariable().getModifiers().getAnnotations().isEmpty())reason="Annotated binding declaration needs an explicit attributed target policy";
        return new Candidate(unit,path,pattern,binding,owner,anchor,false,reason);
    }
    private static void accessible(javax.lang.model.type.TypeMirror type,Scope scope,Trees trees){
        switch(type.getKind()){
            case ARRAY -> accessible(((javax.lang.model.type.ArrayType)type).getComponentType(),scope,trees);
            case DECLARED -> {
                javax.lang.model.type.DeclaredType declared=(javax.lang.model.type.DeclaredType)type;TypeElement element=(TypeElement)declared.asElement();
                if(!trees.isAccessible(scope,element))throw new IllegalArgumentException("Operand type inaccessible at explicit temporary: "+element);
                if(declared.getEnclosingType().getKind()==javax.lang.model.type.TypeKind.DECLARED)accessible(declared.getEnclosingType(),scope,trees);
                for(var argument:declared.getTypeArguments())accessible(argument,scope,trees);
            }
            case WILDCARD -> {var wildcard=(javax.lang.model.type.WildcardType)type;if(wildcard.getExtendsBound()!=null)accessible(wildcard.getExtendsBound(),scope,trees);if(wildcard.getSuperBound()!=null)accessible(wildcard.getSuperBound(),scope,trees);}
            default -> { /* shared renderer rejects unsupported denotations */ }
        }
    }
    /** Preserve a declared wildcard identifier type when javac captures its read expression. */
    private static javax.lang.model.type.TypeMirror operandType(Candidate site, JavacTask task, Trees trees) {
        TreePath expressionPath = new TreePath(site.patternPath(), site.pattern().getExpression());
        javax.lang.model.type.TypeMirror expression = trees.getTypeMirror(expressionPath);
        Scope scope = trees.getScope(site.patternPath());
        accessible(expression, scope, trees);
        if (expression.getKind() == javax.lang.model.type.TypeKind.UNION) {
            if (!(site.pattern().getExpression() instanceof IdentifierTree)) {
                throw new IllegalArgumentException("Union operand needs an exact declared multi-catch parameter");
            }
            Element symbol = trees.getElement(expressionPath);
            if (!(symbol instanceof VariableElement variable) || symbol.getKind() != ElementKind.EXCEPTION_PARAMETER
                    || variable.asType().getKind() != javax.lang.model.type.TypeKind.UNION) {
                throw new IllegalArgumentException("Union operand is not an attributed multi-catch parameter");
            }
            javax.lang.model.type.TypeMirror declared = variable.asType();
            javax.lang.model.type.TypeMirror erased = task.getTypes().erasure(declared);
            if (erased.getKind() != javax.lang.model.type.TypeKind.DECLARED
                    || !task.getTypes().isAssignable(expression, erased)) {
                throw new IllegalArgumentException("Multi-catch erasure is not a declared assignable capture type");
            }
            for (javax.lang.model.type.TypeMirror alternative : ((javax.lang.model.type.UnionType) declared).getAlternatives()) {
                if (!task.getTypes().isAssignable(alternative, erased)) {
                    throw new IllegalArgumentException("Multi-catch alternative is not assignable to the proven erased LUB");
                }
            }
            accessible(erased, scope, trees);
            AttributedVarTypes.denotable(erased, false);
            return erased;
        }
        if (expression.getKind() == javax.lang.model.type.TypeKind.NULL) return expression;
        try { AttributedVarTypes.denotable(expression, false); return expression; }
        catch (IllegalArgumentException captured) {
            if (captured.getMessage() == null || !captured.getMessage().startsWith("Captured variable:")) throw captured;
            if (site.pattern().getExpression() instanceof MethodInvocationTree invocation) {
                Element called = trees.getElement(expressionPath);
                if (!(called instanceof ExecutableElement method)) throw captured;
                javax.lang.model.type.TypeMirror declared = method.getReturnType();
                // A declared wildcard return keeps the exact method's public type algebra.
                if (declared.getKind() == javax.lang.model.type.TypeKind.DECLARED
                        && !containsTypeVariable(declared)
                        && task.getTypes().isAssignable(expression, declared)
                        && task.getTypes().isSameType(task.getTypes().erasure(expression), task.getTypes().erasure(declared))) {
                    accessible(declared, scope, trees);
                    AttributedVarTypes.denotable(declared, false);
                    return declared;
                }
                if (expression.getKind() != javax.lang.model.type.TypeKind.TYPEVAR
                        || !(invocation.getMethodSelect() instanceof MemberSelectTree member)) throw captured;
                TreePath select = new TreePath(expressionPath, invocation.getMethodSelect());
                javax.lang.model.type.TypeMirror receiver = trees.getTypeMirror(new TreePath(select, member.getExpression()));
                if (receiver.getKind() != javax.lang.model.type.TypeKind.DECLARED) throw captured;
                javax.lang.model.type.TypeMirror asMember = task.getTypes().asMemberOf((javax.lang.model.type.DeclaredType) receiver, method);
                if (asMember.getKind() != javax.lang.model.type.TypeKind.EXECUTABLE) throw captured;
                javax.lang.model.type.TypeMirror returned = ((javax.lang.model.type.ExecutableType) asMember).getReturnType();
                if (returned.getKind() != javax.lang.model.type.TypeKind.TYPEVAR
                        || !task.getTypes().isSameType(task.getTypes().erasure(returned), task.getTypes().erasure(expression))) throw captured;
                javax.lang.model.type.TypeMirror upper = ((javax.lang.model.type.TypeVariable) expression).getUpperBound();
                if (upper.getKind() != javax.lang.model.type.TypeKind.DECLARED || containsTypeVariable(upper)
                        || !task.getTypes().isAssignable(expression, upper)
                        || !task.getTypes().isSameType(task.getTypes().erasure(expression), task.getTypes().erasure(upper))) throw captured;
                accessible(upper, scope, trees);
                AttributedVarTypes.denotable(upper, false);
                return upper;
            }
            if (!(site.pattern().getExpression() instanceof IdentifierTree)) throw captured;
            Element symbol = trees.getElement(expressionPath);
            if (!(symbol instanceof VariableElement variable)
                    || !(symbol.getKind() == ElementKind.PARAMETER || symbol.getKind() == ElementKind.LOCAL_VARIABLE)) throw captured;
            javax.lang.model.type.TypeMirror declared = variable.asType();
            if (!task.getTypes().isAssignable(expression, declared)
                    || !task.getTypes().isSameType(task.getTypes().erasure(expression), task.getTypes().erasure(declared))) throw captured;
            accessible(declared, scope, trees);
            AttributedVarTypes.denotable(declared, false);
            return declared;
        }
    }
    private static boolean containsTypeVariable(javax.lang.model.type.TypeMirror type) {
        switch (type.getKind()) {
            case TYPEVAR: return true;
            case ARRAY: return containsTypeVariable(((javax.lang.model.type.ArrayType) type).getComponentType());
            case DECLARED:
                for (javax.lang.model.type.TypeMirror argument : ((javax.lang.model.type.DeclaredType) type).getTypeArguments()) {
                    if (containsTypeVariable(argument)) return true;
                }
                return false;
            case WILDCARD:
                javax.lang.model.type.WildcardType wildcard = (javax.lang.model.type.WildcardType) type;
                return wildcard.getExtendsBound() != null && containsTypeVariable(wildcard.getExtendsBound())
                        || wildcard.getSuperBound() != null && containsTypeVariable(wildcard.getSuperBound());
            default: return false;
        }
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
                CompilationUnitTree unit=entry.getKey();String name=root.relativize(Paths.get(unit.getSourceFile().toUri())).toString().replace(File.separatorChar,'/');String text=new String(originals.get(name),StandardCharsets.UTF_8);List<Candidate> sites=entry.getValue();List<String> reasons=new ArrayList<>();
                for(Candidate site:sites)if(site.reason()!=null)reasons.add("offset="+positions.getStartPosition(unit,site.pattern())+" "+site.reason());
                for(Candidate outer:sites)for(Candidate inner:sites)if(outer!=inner){
                    long start=positions.getStartPosition(unit,outer.pattern()),end=positions.getEndPosition(unit,outer.pattern());long nested=positions.getStartPosition(unit,inner.pattern());
                    if(nested>start&&nested<end)reasons.add("Nested pattern operand requires separate attributed expression ordering proof");
                }
                if(!reasons.isEmpty()){rows.add(name+"\tREJECTED\t"+sites.size()+"\t"+Base64.getEncoder().encodeToString(String.join("\n",reasons).getBytes(StandardCharsets.UTF_8)));continue;}
                for(Candidate site:sites){
                    try{
                        operandType(site,task,trees);
                    }catch(IllegalArgumentException failure){reasons.add("offset="+positions.getStartPosition(unit,site.pattern())+" "+failure.getMessage());}
                }
                if(!reasons.isEmpty()){rows.add(name+"\tREJECTED\t"+sites.size()+"\t"+Base64.getEncoder().encodeToString(String.join("\n",reasons).getBytes(StandardCharsets.UTF_8)));continue;}
                List<Edit> all=new ArrayList<>();Map<Tree,List<String>> declarations=new IdentityHashMap<>();Map<Tree,TreePath> boundaries=new IdentityHashMap<>();int index=0;
                for(Candidate site:sites){
                    String prefix;do{prefix="$oaPattern"+index++ +"_";}while(text.contains(prefix));String holder=prefix+"holder",localClass=prefix+"Holder";
                    int exprStart=position(positions.getStartPosition(unit,site.pattern().getExpression())),exprEnd=position(positions.getEndPosition(unit,site.pattern().getExpression()));Tree type=site.binding().getVariable().getType();String typeText=text.substring(position(positions.getStartPosition(unit,type)),position(positions.getEndPosition(unit,type)));String operand=text.substring(exprStart,exprEnd);
                    javax.lang.model.type.TypeMirror patternMirror=trees.getTypeMirror(TreePath.getPath(unit,type));
                    String runtimeType=AttributedVarTypes.denotable(task.getTypes().erasure(patternMirror),false);
                    javax.lang.model.type.TypeMirror operandMirror=operandType(site,task,trees);String operandType=operandMirror.getKind()==javax.lang.model.type.TypeKind.NULL?"java.lang.Object":AttributedVarTypes.denotable(operandMirror,false);
                    String declaration="final class "+localClass+" { "+operandType+" value; "+typeText+" bound; }\nfinal "+localClass+" "+holder+" = new "+localClass+"();\n";
                    declarations.computeIfAbsent(site.owner(),ignored->new ArrayList<>()).add(declaration);boundaries.put(site.owner(),site.ownerPath());
                    String test="(("+holder+".value = "+operand+") instanceof "+runtimeType+" && (("+holder+".bound = ("+typeText+") "+holder+".value) != null))";
                    all.add(new Edit(position(positions.getStartPosition(unit,site.pattern())),position(positions.getEndPosition(unit,site.pattern())),test));
                    Element binding=trees.getElement(TreePath.getPath(unit,site.binding().getVariable()));if(binding==null)throw new IllegalStateException("Missing original binding element");
                    new TreePathScanner<Void,Void>(){@Override public Void visitIdentifier(IdentifierTree tree,Void ignored){if(binding.equals(trees.getElement(getCurrentPath()))){all.add(new Edit(position(positions.getStartPosition(unit,tree)),position(positions.getEndPosition(unit,tree)),holder+".bound"));}return super.visitIdentifier(tree,ignored);}}.scan(unit,null);
                }
                List<Tree> ordered=new ArrayList<>(boundaries.keySet());ordered.sort(Comparator.comparingLong(tree->positions.getEndPosition(unit,tree)-positions.getStartPosition(unit,tree)));
                for(Tree owner:ordered){
                    int ownerStart=position(positions.getStartPosition(unit,owner)),ownerEnd=position(positions.getEndPosition(unit,owner));String declare=String.join("",declarations.get(owner));
                    if(owner instanceof LambdaExpressionTree lambda){
                        javax.lang.model.type.TypeMirror bodyType=trees.getTypeMirror(new TreePath(boundaries.get(owner),lambda.getBody()));
                        if(bodyType==null||bodyType.getKind()==javax.lang.model.type.TypeKind.VOID||bodyType.getKind()==javax.lang.model.type.TypeKind.ERROR)throw new IllegalArgumentException("Expression lambda needs a genuine nonvoid attributed body");
                        int bodyStart=position(positions.getStartPosition(unit,lambda.getBody())),bodyEnd=position(positions.getEndPosition(unit,lambda.getBody()));List<Edit> inner=new ArrayList<>();for(Edit edit:all)if(edit.start()>=bodyStart&&edit.end()<=bodyEnd)inner.add(edit);
                        String body=apply(text,bodyStart,bodyEnd,inner);all.removeAll(inner);all.add(new Edit(bodyStart,bodyEnd,"{\n"+declare+"return "+body+";\n}"));
                    }else if(boundaries.get(owner).getParentPath().getLeaf() instanceof BlockTree){
                        all.add(new Edit(ownerStart,ownerStart,declare));
                    }else{
                        List<Edit> inner=new ArrayList<>();for(Edit edit:all)if(edit.start()>=ownerStart&&edit.end()<=ownerEnd)inner.add(edit);String body=apply(text,ownerStart,ownerEnd,inner);all.removeAll(inner);all.add(new Edit(ownerStart,ownerEnd,"{\n"+declare+body+"\n}"));
                    }
                }
                String result=apply(text,0,text.length(),all);products.put(name,result.getBytes(StandardCharsets.UTF_8));rows.add(name+"\tSUPPORTED\t"+sites.size()+"\t0");
            }
        }
        for(var original:originals.entrySet())if(!Arrays.equals(original.getValue(),Files.readAllBytes(root.resolve(original.getKey()))))throw new IllegalStateException("Canonical input drift");Files.createDirectories(output);for(var product:products.entrySet())for(String side:List.of("pre","post")){Path path=output.resolve(side).resolve(product.getKey());Files.createDirectories(path.getParent());Files.write(path,side.equals("pre")?originals.get(product.getKey()):product.getValue());}Files.write(output.resolve("owner-status.tsv"),rows,StandardCharsets.UTF_8);System.out.println("PASS public attributed pattern conversion preflight owners="+originals.size()+" supported="+products.size());
    }
}
