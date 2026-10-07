package dev.openallay.build;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;

/** Public compiler switch language lowering, selector once and exact break/yield targets. */
public final class CanonicalSwitchPort {
    private record Edit(int start,int end,String replacement) {}
    private record Site(CompilationUnitTree unit,TreePath path,Tree tree,boolean expression) {}
    private static int pos(long value){if(value<0||value>Integer.MAX_VALUE)throw new IllegalArgumentException("Missing public source position");return(int)value;}
    private static Tree unparen(Tree tree){while(tree instanceof ParenthesizedTree p)tree=p.getExpression();return tree;}
    private static String apply(String text,int start,int end,List<Edit> edits){StringBuilder out=new StringBuilder(text.substring(start,end));List<Edit> sorted=new ArrayList<>(edits);sorted.sort(Comparator.comparingInt(Edit::start).reversed());int previous=end;for(Edit edit:sorted){if(edit.start()<start||edit.end()>end||edit.end()>previous)throw new IllegalArgumentException("Overlapping switch source edits");out.replace(edit.start()-start,edit.end()-start,edit.replacement());previous=edit.start();}return out.toString();}
    private static String slice(String text,CompilationUnitTree unit,SourcePositions positions,Tree tree){return text.substring(pos(positions.getStartPosition(unit,tree)),pos(positions.getEndPosition(unit,tree)));}
    private static boolean abrupt(Tree tree){if(tree instanceof ReturnTree||tree instanceof ThrowTree||tree instanceof ContinueTree||tree instanceof BreakTree||tree instanceof YieldTree)return true;if(tree instanceof BlockTree block&&!block.getStatements().isEmpty())return abrupt(block.getStatements().get(block.getStatements().size()-1));if(tree instanceof IfTree branch&&branch.getElseStatement()!=null)return abrupt(branch.getThenStatement())&&abrupt(branch.getElseStatement());return false;}
    private static String labels(CaseTree rule){List<? extends ExpressionTree> expressions=rule.getExpressions();if(expressions.isEmpty())return "default:";List<String> labels=new ArrayList<>();for(ExpressionTree expression:expressions){if(!(expression instanceof LiteralTree||expression instanceof IdentifierTree||expression instanceof UnaryTree))throw new IllegalArgumentException("Unsupported pattern/null switch label");labels.add("case "+expression+":");}return String.join("\n",labels);}
    private static boolean enumSelector(Trees trees,TreePath path,ExpressionTree selector){TypeMirror type=trees.getTypeMirror(new TreePath(path,selector));return type.getKind()==TypeKind.DECLARED&&((DeclaredType)type).asElement().getKind()==ElementKind.ENUM;}
    private record Lift(TreePath boundary,String expression,int from,int to,String prefix,boolean lambda) {}
    private static String denotableAt(TreePath path,Trees trees){return AttributedVarTypes.denotable(trees.getTypeMirror(path),false);}
    private static Lift embeddedLift(Site site,String text,SourcePositions positions,Trees trees,String result,String evaluation){
        CompilationUnitTree unit=site.unit();Tree child=site.tree();TreePath cursor=site.path().getParentPath();List<Edit> edits=new ArrayList<>();StringBuilder prefix=new StringBuilder();int index=0;
        edits.add(new Edit(pos(positions.getStartPosition(unit,child)),pos(positions.getEndPosition(unit,child)),result));
        while(cursor!=null){Tree parent=cursor.getLeaf();
            if(parent instanceof ParenthesizedTree){child=parent;cursor=cursor.getParentPath();continue;}
            if(parent instanceof LambdaExpressionTree lambda&&lambda.getBody()==child&&lambda.getBodyKind()==LambdaExpressionTree.BodyKind.EXPRESSION){
                String expression=apply(text,pos(positions.getStartPosition(unit,child)),pos(positions.getEndPosition(unit,child)),edits);return new Lift(cursor,expression,pos(positions.getStartPosition(unit,child)),pos(positions.getEndPosition(unit,child)),prefix+evaluation,true);
            }
            if(parent instanceof ReturnTree returned&&returned.getExpression()==child){String expression=apply(text,pos(positions.getStartPosition(unit,child)),pos(positions.getEndPosition(unit,child)),edits);return new Lift(cursor,expression,pos(positions.getStartPosition(unit,parent)),pos(positions.getEndPosition(unit,parent)),prefix+evaluation,false);}
            if(parent instanceof ExpressionStatementTree expression&&expression.getExpression()==child){String replacement=apply(text,pos(positions.getStartPosition(unit,child)),pos(positions.getEndPosition(unit,child)),edits);return new Lift(cursor,replacement,pos(positions.getStartPosition(unit,parent)),pos(positions.getEndPosition(unit,parent)),prefix+evaluation,false);}
            if(parent instanceof VariableTree variable&&variable.getInitializer()==child&&cursor.getParentPath().getLeaf() instanceof BlockTree){String expression=apply(text,pos(positions.getStartPosition(unit,parent)),pos(positions.getEndPosition(unit,parent)),edits);return new Lift(cursor,expression,pos(positions.getStartPosition(unit,parent)),pos(positions.getEndPosition(unit,parent)),prefix+evaluation,false);}
            if(parent instanceof AssignmentTree assignment && assignment.getExpression()==child && assignment.getVariable() instanceof IdentifierTree){
                child=parent;cursor=cursor.getParentPath();continue;
            }
            if(parent instanceof ConditionalExpressionTree conditional && (conditional.getTrueExpression()==child||conditional.getFalseExpression()==child)){
                String temporary=result+"_conditional"+index++,type=denotableAt(cursor,trees);
                String condition=slice(text,unit,positions,conditional.getCondition());
                String active=apply(text,pos(positions.getStartPosition(unit,child)),pos(positions.getEndPosition(unit,child)),edits);
                String other=slice(text,unit,positions,conditional.getTrueExpression()==child?conditional.getFalseExpression():conditional.getTrueExpression());
                String branch=prefix+evaluation+temporary+" = "+active+";\n";
                String trueBranch=conditional.getTrueExpression()==child?branch:temporary+" = "+other+";\n";
                String falseBranch=conditional.getFalseExpression()==child?branch:temporary+" = "+other+";\n";
                int from=pos(positions.getStartPosition(unit,parent)),to=pos(positions.getEndPosition(unit,parent));
                edits.removeIf(edit->edit.start()>=from&&edit.end()<=to);edits.add(new Edit(from,to,temporary));
                prefix.setLength(0);prefix.append(type).append(' ').append(temporary).append(";\nif (").append(condition).append(") {\n").append(trueBranch).append("} else {\n").append(falseBranch).append("}\n");
                evaluation="";child=parent;cursor=cursor.getParentPath();continue;
            }
            if(parent instanceof MemberSelectTree member && member.getExpression()==child){
                if(trees.getElement(cursor) instanceof javax.lang.model.element.VariableElement)throw new IllegalArgumentException("Embedded instance field access needs dereference timing proof");
                child=parent;cursor=cursor.getParentPath();continue;
            }
            List<? extends ExpressionTree> previous=List.of();
            if(parent instanceof MethodInvocationTree call){
                int argument=call.getArguments().indexOf(child);
                if(argument<0){
                    if(call.getMethodSelect()==child && child instanceof MemberSelectTree){child=parent;cursor=cursor.getParentPath();continue;}
                    throw new IllegalArgumentException("Switch in invocation receiver/typearguments requires separate timing proof");
                }
                List<ExpressionTree> operands=new ArrayList<>();Element method=trees.getElement(cursor);
                if(!(method instanceof ExecutableElement executable))throw new IllegalArgumentException("Invocation method has no attributed executable");
                if(call.getMethodSelect() instanceof MemberSelectTree member){TreePath receiverPath=new TreePath(new TreePath(cursor,call.getMethodSelect()),member.getExpression());Element qualifier=trees.getElement(receiverPath);if(!(qualifier instanceof TypeElement))operands.add(member.getExpression());}
                operands.addAll(call.getArguments().subList(0,argument));previous=operands;
            }else if(parent instanceof NewClassTree constructor){int argument=constructor.getArguments().indexOf(child);if(argument<0||constructor.getEnclosingExpression()!=null||constructor.getClassBody()!=null)throw new IllegalArgumentException("Qualified/anonymous constructor switch needs evaluation proof");previous=constructor.getArguments().subList(0,argument);
            }else if(parent instanceof BinaryTree binary&&binary.getKind()==Tree.Kind.PLUS){
                if(binary.getRightOperand()==child){
                    javax.lang.model.type.TypeMirror left=trees.getTypeMirror(TreePath.getPath(unit,binary.getLeftOperand()));
                    if(left.getKind()==javax.lang.model.type.TypeKind.DECLARED && !((TypeElement)((javax.lang.model.type.DeclaredType)left).asElement()).getQualifiedName().contentEquals("java.lang.String"))throw new IllegalArgumentException("Object-toString concatenation timing needs conversion proof before selector");
                    previous=List.of(binary.getLeftOperand());
                }else if(binary.getLeftOperand()!=child)throw new IllegalArgumentException("Unknown binary operand");
            }else throw new IllegalArgumentException("Embedded switch operand parent not proven eager left-to-right: "+parent.getKind());
            StringBuilder level=new StringBuilder();
            for(ExpressionTree prior:previous){TreePath operandPath=TreePath.getPath(unit,prior);String type=denotableAt(operandPath,trees),temporary=result+"_prior"+index++;String source=slice(text,unit,positions,prior);level.append("final ").append(type).append(' ').append(temporary).append(" = ").append(source).append(";\n");edits.add(new Edit(pos(positions.getStartPosition(unit,prior)),pos(positions.getEndPosition(unit,prior)),temporary));}
            prefix.insert(0,level);child=parent;cursor=cursor.getParentPath();
        }
        throw new IllegalArgumentException("Embedded switch lacks supported statement/lambda boundary");
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("completeActualRoot genuineProductionClasspath selectedOwners freshExternalOutput");Path root=Paths.get(args[0]).toAbsolutePath().normalize(),output=Paths.get(args[3]).toAbsolutePath().normalize();if(Files.exists(output)||output.startsWith(root)||root.startsWith(output))throw new IllegalArgumentException("Fresh externaloutput required");Set<String> selected=new TreeSet<>(Files.readAllLines(Paths.get(args[2]),StandardCharsets.UTF_8));List<File> files=new ArrayList<>();try(var stream=Files.walk(root)){stream.filter(p->p.toString().endsWith(".java")).sorted().forEach(p->files.add(p.toFile()));}
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();Map<String,byte[]> before=new TreeMap<>(),after=new TreeMap<>();List<String> rows=new ArrayList<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--release","17","-proc:none","-encoding","UTF-8","-classpath",args[1]),null,manager.getJavaFileObjectsFromFiles(files));List<CompilationUnitTree> units=new ArrayList<>();task.parse().forEach(units::add);Trees trees=Trees.instance(task);SourcePositions positions=trees.getSourcePositions();Map<CompilationUnitTree,List<Site>> owners=new LinkedHashMap<>();
            for(CompilationUnitTree unit:units){String name=root.relativize(Paths.get(unit.getSourceFile().toUri())).toString().replace(File.separatorChar,'/');if(!selected.contains(name))continue;before.put(name,Files.readAllBytes(root.resolve(name)));List<Site> sites=new ArrayList<>();new TreePathScanner<Void,Void>(){
                @Override public Void visitSwitchExpression(SwitchExpressionTree tree,Void unused){sites.add(new Site(unit,getCurrentPath(),tree,true));return super.visitSwitchExpression(tree,unused);}
                @Override public Void visitSwitch(SwitchTree tree,Void unused){if(tree.getCases().stream().anyMatch(c->c.getCaseKind()==CaseTree.CaseKind.RULE))sites.add(new Site(unit,getCurrentPath(),tree,false));return super.visitSwitch(tree,unused);}
            }.scan(unit,null);owners.put(unit,sites);}
            if(!before.keySet().equals(selected))throw new IllegalArgumentException("Selected sourceclosure differs");task.analyze();for(Diagnostic<?> diagnostic:diagnostics.getDiagnostics())if(diagnostic.getKind()==Diagnostic.Kind.ERROR)throw new IllegalStateException("Complete sourceattribution failed: "+diagnostic);
            for(var owner:owners.entrySet()){
                CompilationUnitTree unit=owner.getKey();String name=root.relativize(Paths.get(unit.getSourceFile().toUri())).toString().replace(File.separatorChar,'/'),text=new String(before.get(name),StandardCharsets.UTF_8);List<Site> sites=owner.getValue();sites.sort(Comparator.comparingLong(s->positions.getEndPosition(unit,s.tree())-positions.getStartPosition(unit,s.tree())));List<Edit> changes=new ArrayList<>();List<String> reasons=new ArrayList<>();int count=0;
                for(Site site:sites){try{
                    List<? extends CaseTree> cases=site.expression()?((SwitchExpressionTree)site.tree()).getCases():((SwitchTree)site.tree()).getCases();ExpressionTree selector=site.expression()?((SwitchExpressionTree)site.tree()).getExpression():((SwitchTree)site.tree()).getExpression();
                    for(CaseTree rule:cases)if(rule.getCaseKind()!=CaseTree.CaseKind.RULE)throw new IllegalArgumentException("Mixed/colon cases require explicit fallthrough proof");String label="$oaSwitch"+count++ +"_exit";while(text.contains(label))label+="_";String result=label+"_result";StringBuilder body=new StringBuilder("switch (").append(slice(text,unit,positions,selector)).append(") {\n");boolean defaultCase=false;
                    for(CaseTree rule:cases){body.append(labels(rule)).append("\n{\n");defaultCase|=rule.getExpressions().isEmpty();Tree value=rule.getBody();List<Edit> caseEdits=new ArrayList<>();int start=pos(positions.getStartPosition(unit,value)),end=pos(positions.getEndPosition(unit,value));for(Edit edit:changes)if(edit.start()>=start&&edit.end()<=end)caseEdits.add(edit);
                        if(site.expression()){
                            if(value instanceof ExpressionTree)body.append(result).append(" = ").append(apply(text,start,end,caseEdits)).append("; break ").append(label).append(";\n");
                            else if(value instanceof ThrowTree)body.append(apply(text,start,end,caseEdits)).append('\n');
                            else if(value instanceof BlockTree){
                                final boolean[] invalid={false};String exit=label;
                                new TreePathScanner<Void,Void>(){
                                    @Override public Void visitSwitchExpression(SwitchExpressionTree nested,Void unused){return null;}
                                    @Override public Void visitYield(YieldTree yield,Void unused){int a=pos(positions.getStartPosition(unit,yield)),b=pos(positions.getEndPosition(unit,yield));for(Edit edit:caseEdits)if(edit.start()>=a&&edit.end()<=b)invalid[0]=true;caseEdits.add(new Edit(a,b,"{ "+result+" = "+slice(text,unit,positions,yield.getValue())+"; break "+exit+"; }"));return null;}
                                    @Override public Void visitBreak(BreakTree branch,Void unused){if(branch.getLabel()==null)invalid[0]=true;return super.visitBreak(branch,unused);}
                                }.scan(TreePath.getPath(unit,value),null);
                                if(invalid[0])throw new IllegalArgumentException("Yield nestedexpression/unlabeled innerbreak target needs separate controlflow proof");body.append(apply(text,start,end,caseEdits)).append('\n');
                            }else throw new IllegalArgumentException("Unsupported switch expression casebody");
                        }else{body.append(apply(text,start,end,caseEdits)).append('\n');if(!abrupt(value))body.append("break;\n");}
                        body.append("}\n");
                    }
                    if(site.expression()&&!defaultCase){if(!enumSelector(trees,site.path(),selector))throw new IllegalArgumentException("Missing default without genuine exhaustive enumselector");body.append("default: throw new java.lang.IncompatibleClassChangeError();\n");}
                    body.append("}\n");int from=pos(positions.getStartPosition(unit,site.tree())),to=pos(positions.getEndPosition(unit,site.tree()));String replacement=body.toString();
                    if(site.expression()){
                        TypeMirror mirror=trees.getTypeMirror(site.path());String type=AttributedVarTypes.denotable(mirror,false);TreePath boundary=site.path().getParentPath();while(boundary!=null&&boundary.getLeaf() instanceof ParenthesizedTree)boundary=boundary.getParentPath();Tree parent=boundary==null?null:boundary.getLeaf();String evaluation=type+" "+result+";\n"+label+": {\n"+replacement+"}\n";
                        if(parent instanceof ReturnTree returned&&unparen(returned.getExpression())==site.tree()){from=pos(positions.getStartPosition(unit,parent));to=pos(positions.getEndPosition(unit,parent));replacement="{\n"+evaluation+"return "+result+";\n}";}
                        else if(parent instanceof VariableTree variable&&unparen(variable.getInitializer())==site.tree()&&boundary.getParentPath().getLeaf() instanceof BlockTree){from=pos(positions.getStartPosition(unit,parent));to=pos(positions.getEndPosition(unit,parent));String declaration=slice(text,unit,positions,parent);int relative=pos(positions.getStartPosition(unit,site.tree()))-from;declaration=declaration.substring(0,relative)+result+declaration.substring(pos(positions.getEndPosition(unit,site.tree()))-from);replacement=evaluation+declaration;}
                        else if(parent instanceof AssignmentTree assignment&&unparen(assignment.getExpression())==site.tree()&&assignment.getVariable() instanceof IdentifierTree&&boundary.getParentPath().getLeaf() instanceof ExpressionStatementTree){Tree statement=boundary.getParentPath().getLeaf();from=pos(positions.getStartPosition(unit,statement));to=pos(positions.getEndPosition(unit,statement));replacement="{\n"+evaluation+slice(text,unit,positions,assignment.getVariable())+" = "+result+";\n}";}
                        else {
                            Lift lift=embeddedLift(site,text,positions,trees,result,evaluation);from=lift.from();to=lift.to();Tree boundaryTree=lift.boundary().getLeaf();
                            if(lift.lambda())replacement="{\n"+lift.prefix()+"return "+lift.expression()+";\n}";
                            else if(boundaryTree instanceof ReturnTree)replacement="{\n"+lift.prefix()+"return "+lift.expression()+";\n}";
                            else if(boundaryTree instanceof VariableTree)replacement=lift.prefix()+lift.expression();
                            else replacement="{\n"+lift.prefix()+lift.expression()+";\n}";
                        }
                    }
                    List<Edit> nested=new ArrayList<>();for(Edit edit:changes)if(edit.start()>=from&&edit.end()<=to)nested.add(edit);changes.removeAll(nested);changes.add(new Edit(from,to,replacement));
                }catch(IllegalArgumentException failure){reasons.add("offset="+positions.getStartPosition(unit,site.tree())+" "+failure.getMessage());}}
                if(!reasons.isEmpty()){rows.add(name+"\tREJECTED\t"+sites.size()+"\t"+Base64.getEncoder().encodeToString(String.join("\n",reasons).getBytes(StandardCharsets.UTF_8)));continue;}
                after.put(name,apply(text,0,text.length(),changes).getBytes(StandardCharsets.UTF_8));rows.add(name+"\tSUPPORTED\t"+sites.size()+"\t0");
            }
        }
        for(var original:before.entrySet())if(!Arrays.equals(original.getValue(),Files.readAllBytes(root.resolve(original.getKey()))))throw new IllegalStateException("Canonical source drift");Files.createDirectories(output);for(var item:after.entrySet())for(String side:List.of("pre","post")){Path path=output.resolve(side).resolve(item.getKey());Files.createDirectories(path.getParent());Files.write(path,side.equals("pre")?before.get(item.getKey()):item.getValue());}Files.write(output.resolve("owner-status.tsv"),rows,StandardCharsets.UTF_8);System.out.println("PASS publicswitch sourceclassification owners="+before.size()+" supported="+after.size());
    }
}
