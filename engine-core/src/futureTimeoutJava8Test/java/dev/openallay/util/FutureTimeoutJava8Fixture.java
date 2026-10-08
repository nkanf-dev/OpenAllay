package dev.openallay.util;
import java.util.concurrent.*;import java.lang.reflect.InvocationTargetException;
/** Addition-only completeOnTimeout oracle; prior orTimeout proof is reused, not rerun. */
public final class FutureTimeoutJava8Fixture {
 public static void main(String[]args)throws Throwable{boolean modern=args.length==0;if(!modern&&!"1.8".equals(System.getProperty("java.specification.version")))throw new AssertionError("true8");
  CompletableFuture<String> done=CompletableFuture.completedFuture("done");check(complete(done,"timeout",0,TimeUnit.MILLISECONDS,modern)==done,"identity");check(done.join().equals("done"),"terminal success");
  CompletableFuture<String> early=new CompletableFuture<>();check(complete(early,"timeout",2,TimeUnit.SECONDS,modern)==early,"pending identity");early.complete("early");check(early.join().equals("early"),"early preserved");
  CompletableFuture<String> nullable=new CompletableFuture<>();check(complete(nullable,null,10,TimeUnit.MILLISECONDS,modern)==nullable,"null identity");check(nullable.join()==null,"null deadline value");check(!nullable.complete("late"),"late cannot replace");
  CompletableFuture<String> negative=new CompletableFuture<>();complete(negative,"negative",-1,TimeUnit.MILLISECONDS,modern);check(negative.join().equals("negative"),"negative deadline");
  RuntimeException exact=new RuntimeException("exact");CompletableFuture<String> failed=new CompletableFuture<>();failed.completeExceptionally(exact);complete(failed,"timeout",0,TimeUnit.MILLISECONDS,modern);try{failed.join();throw new AssertionError("failure lost");}catch(CompletionException e){check(e.getCause()==exact,"exact failure");}
  CompletableFuture<String> canceled=new CompletableFuture<>();complete(canceled,"timeout",2,TimeUnit.SECONDS,modern);canceled.cancel(false);check(canceled.isCancelled(),"cancel retained");
  try{complete(done,"timeout",1,null,modern);throw new AssertionError("nullunit");}catch(NullPointerException expected){}
  System.out.println("identity/done/early/null-deadline/negative/exact-failure/cancel/nullunit");System.out.println("PASS standard future completeOnTimeout originalmodern=canonicaltrue8");
 }
 @SuppressWarnings("unchecked")static<T>CompletableFuture<T>complete(CompletableFuture<T>f,T value,long time,TimeUnit unit,boolean modern)throws Throwable{if(!modern)return Java8Futures.completeOnTimeout(f,value,time,unit);try{return(CompletableFuture<T>)CompletableFuture.class.getMethod("completeOnTimeout",Object.class,long.class,TimeUnit.class).invoke(f,value,time,unit);}catch(InvocationTargetException e){throw e.getCause();}}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
}
